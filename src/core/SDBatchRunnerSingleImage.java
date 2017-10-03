package core;

import edu.umassmed.omega.commons.data.trajectoryElements.OmegaROI;
import edu.umassmed.omega.sdSbalzariniPlugin.runnable.SDWorker2;
import gui.OmegaGenericToolGUI;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.StackStatistics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SDBatchRunnerSingleImage implements Runnable {
	
	private final File workingDir;
	private final Double cutoff;
	private final Float percentile, threshold;
	private final boolean percAbs;
	private final int radius, c, z;

	private final OmegaGenericToolGUI gui;
	
	// RADIUS 3
	// CUTOFF 0.001
	// PERCENTILE 0.500

	public SDBatchRunnerSingleImage(final OmegaGenericToolGUI gui,
			final File workingDir, final int radius, final double cutoff,
			final float percentile, final float threshold,
			final boolean percAbs, final int channel, final int plane) {
		this.workingDir = workingDir;
		this.radius = radius;
		this.cutoff = cutoff;
		this.percentile = percentile;
		this.threshold = threshold;
		this.percAbs = percAbs;
		this.c = channel;
		this.z = plane;

		this.gui = gui;
	}

	@Override
	public void run() {
		if (!this.workingDir.isDirectory())
			return;
		
		this.gui.appendOutput("Launched on " + this.workingDir.getName());
		
		final File log = new File(this.workingDir.getAbsoluteFile()
				+ File.separator + "SDBatchLog.txt");
		FileWriter fwl = null;
		try {
			fwl = new FileWriter(log, false);
		} catch (final IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (fwl == null)
			return;
		final BufferedWriter bwl = new BufferedWriter(fwl);
		final ExecutorService exec = Executors.newFixedThreadPool(5);

		final List<SDWorker2> workers = new ArrayList<SDWorker2>();
		Float gMin = Float.MAX_VALUE, gMax = 0F;
		final Map<Integer, ImagePlus> images = new LinkedHashMap<Integer, ImagePlus>();
		final File test = new File(this.workingDir.getAbsoluteFile()
				+ File.separator + "logs" + File.separator
				+ "SDOutputSingle.txt");
		if (test.exists()) {
			this.gui.appendOutput(this.workingDir.getName()
					+ " previously analyzed");
			try {
				bwl.write(this.workingDir.getName() + " previously analyzed\n");
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		for (final File f3 : this.workingDir.listFiles()) {
			if (f3.isDirectory() || !f3.getName().contains(".tif")) {
				continue;
			}
			final String name = f3.getName();
			String num = name.replace("test_", "");
			num = num.replace(".tif", "");
			final Integer index = Integer.valueOf(num);
			final ImagePlus is = new ImagePlus(f3.getAbsolutePath());
			Float min = Float.MAX_VALUE, max = 0F;
			final StackStatistics stack_stats = new StackStatistics(is);
			max = (float) stack_stats.max;
			min = (float) stack_stats.min;
			if (gMin > min) {
				gMin = min;
			}
			if (gMax < max) {
				gMax = max;
			}
			images.put(index, is);
		}

		try {
			Thread.sleep(600);
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for (final Integer index : images.keySet()) {
			final ImagePlus is = images.get(index);
			final ImageStack lis = is.getImageStack();
			final SDWorker2 worker = new SDWorker2(lis, index - 1, this.radius,
					this.cutoff, this.percentile, this.threshold, this.percAbs,
					this.c, this.z);
			worker.setGlobalMax(gMax);
			worker.setGlobalMin(gMin);
			// exec.submit(worker);
			exec.execute(worker);
			workers.add(worker);
		}

		this.gui.appendOutput(this.workingDir.getName()
				+ " all workers launched");
		try {
			bwl.write(this.workingDir.getName() + " all workers launched\n");
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// try {
		// Thread.sleep(600);
		// } catch (final InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		
		final List<SDWorker2> completedWorkers = new ArrayList<SDWorker2>();
		while (!workers.isEmpty()) {
			for (final SDWorker2 runnable : workers) {
				if (!runnable.isJobCompleted()) {
					continue;
				}
				completedWorkers.add(runnable);
			}
			workers.removeAll(completedWorkers);
		}

		this.gui.appendOutput(this.workingDir.getName()
				+ " all workers completed");
		try {
			bwl.write(this.workingDir.getName() + " all workers completed\n");
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		int counter = 0;
		final File output = new File(this.workingDir.getAbsoluteFile()
				+ File.separator + "logs" + File.separator
				+ "SDOutputSingle.txt");
		FileWriter fw = null;
		try {
			fw = new FileWriter(output, false);
		} catch (final IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (fw == null)
			return;
		final BufferedWriter bw = new BufferedWriter(fw);
		while (counter < completedWorkers.size()) {
			for (final SDWorker2 worker : completedWorkers) {
				if (worker.getIndex() != counter) {
					continue;
				}
				counter++;
				final List<OmegaROI> particles = worker.getResultingParticles();
				final Map<OmegaROI, Map<String, Object>> values = worker
						.getParticlesAdditionalValues();
				final StringBuffer sb = new StringBuffer();
				for (final OmegaROI roi : particles) {
					sb.append(roi.getFrameIndex());
					sb.append("\t");
					sb.append(roi.getX());
					sb.append("\t");
					sb.append(roi.getY());
					sb.append("\t");
					final Map<String, Object> roiValues = values.get(roi);
					for (final String s : roiValues.keySet()) {
						sb.append(roiValues.get(s));
						sb.append("\t");
					}
					sb.append("\n");
					try {
						bw.write(sb.toString());
					} catch (final IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		try {
			bw.close();
			fw.close();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		this.gui.appendOutput(this.workingDir.getName() + " finished");
		try {
			bwl.write(this.workingDir.getName() + " finished\n");
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// try {
		// Thread.sleep(1800);
		// } catch (final InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		exec.shutdown();
		this.gui.appendOutput("Try to shut down exec");
		try {
			bwl.write("Try to shut down exec\n");
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			if (!exec.awaitTermination(5, TimeUnit.MINUTES)) {
				this.gui.appendOutput("Exec not shutted down after 5 minutes");
				try {
					bwl.write("Exec not shutted down after 5 minutes\n");
				} catch (final IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				exec.shutdownNow();
				System.exit(0);
			}
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			bwl.close();
			fwl.close();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.gui.appendOutput("###############################################");
	}
}