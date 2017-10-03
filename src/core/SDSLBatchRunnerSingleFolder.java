package core;

import edu.umassmed.omega.commons.constants.OmegaConstantsAlgorithmParameters;
import edu.umassmed.omega.commons.data.analysisRunElements.OmegaParameter;
import edu.umassmed.omega.commons.data.analysisRunElements.OmegaParticleDetectionRun;
import edu.umassmed.omega.commons.data.coreElements.OmegaPlane;
import edu.umassmed.omega.commons.data.trajectoryElements.OmegaROI;
import edu.umassmed.omega.commons.data.trajectoryElements.OmegaTrajectory;
import edu.umassmed.omega.plSbalzariniPlugin.runnable.PLRunner;
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

public class SDSLBatchRunnerSingleFolder implements Runnable {

	private final File workingDir;
	private final Double cutoff;
	private final Float percentile, threshold, displacement, objectFeature,
			dynamics;
	private final boolean percAbs;
	private final Integer radius, c, z, linkrange, minLength;
	private final String movType, optimizer;
	
	private final OmegaGenericToolGUI gui;

	// RADIUS 3
	// CUTOFF 0.001
	// PERCENTILE 0.500
	
	public SDSLBatchRunnerSingleFolder(final OmegaGenericToolGUI gui,
			final File workingDir, final int radius, final double cutoff,
			final float percentile, final float threshold,
			final boolean percAbs, final int channel, final int plane,
			final Float displacement, final Integer linkrange,
			final String movType, final Float objectFeature,
			final Float dynamics, final String optimizer,
			final Integer minLength) {
		this.workingDir = workingDir;
		this.radius = radius;
		this.cutoff = cutoff;
		this.percentile = percentile;
		this.threshold = threshold;
		this.percAbs = percAbs;
		this.c = channel;
		this.z = plane;
		
		this.displacement = displacement;
		this.linkrange = linkrange;
		this.movType = movType;
		this.objectFeature = objectFeature;
		this.dynamics = dynamics;
		this.optimizer = optimizer;
		this.minLength = minLength;
		
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
		for (final File f1 : this.workingDir.listFiles()) {
			if (!f1.isDirectory()) {
				continue;
			}

			final List<SDWorker2> workers = new ArrayList<SDWorker2>();
			Float gMin = Float.MAX_VALUE, gMax = 0F;
			final Map<Integer, ImagePlus> images = new LinkedHashMap<Integer, ImagePlus>();
			final File test = new File(f1.getAbsoluteFile() + File.separator
					+ "logs" + File.separator + "SDOutput.txt");
			if (test.exists()) {
				this.gui.appendOutput(f1.getName() + " previously analyzed");
				try {
					bwl.write(f1.getName() + " previously analyzed\n");
				} catch (final IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				continue;
			}
			for (final File f3 : f1.listFiles()) {
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
				final SDWorker2 worker = new SDWorker2(lis, index - 1,
						this.radius, this.cutoff, this.percentile,
						this.threshold, this.percAbs, this.c, this.z);
				worker.setGlobalMax(gMax);
				worker.setGlobalMin(gMin);
				// exec.submit(worker);
				exec.execute(worker);
				workers.add(worker);
			}

			this.gui.appendOutput(f1.getName() + " all workers launched");
			try {
				bwl.write(f1.getName() + " all workers launched\n");
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

			this.gui.appendOutput(f1.getName() + " all workers completed");
			try {
				bwl.write(f1.getName() + " all workers completed\n");
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			int counter = 0;
			final File output = new File(f1.getAbsoluteFile() + File.separator
					+ "logs" + File.separator + "SDOutput.txt");
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
			final Map<OmegaPlane, List<OmegaROI>> resultingParticles = new LinkedHashMap<OmegaPlane, List<OmegaROI>>();
			final Map<OmegaROI, Map<String, Object>> resultingParticlesValues = new LinkedHashMap<OmegaROI, Map<String, Object>>();
			while (counter < completedWorkers.size()) {
				for (final SDWorker2 worker : completedWorkers) {
					if (worker.getIndex() != counter) {
						continue;
					}
					counter++;
					final List<OmegaROI> particles = worker
							.getResultingParticles();
					resultingParticles.put(worker.getFrame(), particles);
					final Map<OmegaROI, Map<String, Object>> values = worker
							.getParticlesAdditionalValues();
					resultingParticlesValues.putAll(values);
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

			final OmegaParticleDetectionRun opdr = new OmegaParticleDetectionRun(
					null, null, resultingParticles, resultingParticlesValues);
			final List<OmegaParameter> params = new ArrayList<OmegaParameter>();
			params.add(new OmegaParameter(
					OmegaConstantsAlgorithmParameters.PARAM_DISPLACEMENT,
					this.displacement));
			params.add(new OmegaParameter(
					OmegaConstantsAlgorithmParameters.PARAM_LINKRANGE,
					this.linkrange));
			params.add(new OmegaParameter(
					OmegaConstantsAlgorithmParameters.PARAM_MOVTYPE,
					this.movType));
			params.add(new OmegaParameter(
					OmegaConstantsAlgorithmParameters.PARAM_OBJFEATURE,
					this.objectFeature));
			params.add(new OmegaParameter(
					OmegaConstantsAlgorithmParameters.PARAM_DYNAMICS,
					this.dynamics));
			params.add(new OmegaParameter(
					OmegaConstantsAlgorithmParameters.PARAM_OPTIMIZER,
					this.optimizer));
			params.add(new OmegaParameter(
					OmegaConstantsAlgorithmParameters.PARAM_MINPOINTS,
					this.minLength));
			final Map<Integer, Map<OmegaParticleDetectionRun, List<OmegaParameter>>> particlesToProcess = new LinkedHashMap<Integer, Map<OmegaParticleDetectionRun, List<OmegaParameter>>>();
			final Map<OmegaParticleDetectionRun, List<OmegaParameter>> particleToProcess = new LinkedHashMap<OmegaParticleDetectionRun, List<OmegaParameter>>();
			particleToProcess.put(opdr, params);
			particlesToProcess.put(1, particleToProcess);
			final PLRunner plr = new PLRunner(particlesToProcess);
			plr.run();

			while (!plr.isJobCompleted()) {

			}

			final List<OmegaTrajectory> tracks = plr.getResultingTrajectories()
					.get(1).get(opdr);

			final File output2 = new File(f1.getAbsoluteFile() + File.separator
					+ "logs" + File.separator + "PLOutput.txt");
			FileWriter fw2 = null;
			try {
				fw2 = new FileWriter(output2, false);
			} catch (final IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if (fw2 == null)
				return;

			final BufferedWriter bw2 = new BufferedWriter(fw2);
			for (final OmegaTrajectory track : tracks) {
				final StringBuffer sb = new StringBuffer();
				sb.append("% Trajectory id:\t");
				sb.append(track.getName());
				sb.append("\n");
				for (final OmegaROI roi : track.getROIs()) {

					sb.append(roi.getFrameIndex());
					sb.append("\t");
					sb.append(roi.getX());
					sb.append("\t");
					sb.append(roi.getY());
					sb.append("\t");
					final Map<String, Object> roiValues = resultingParticlesValues
							.get(roi);
					for (final String s : roiValues.keySet()) {
						sb.append(roiValues.get(s));
						sb.append("\t");
					}
					sb.append("\n");
				}
				sb.append("\n");
				try {
					bw2.write(sb.toString());
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
			try {
				bw2.close();
				fw2.close();
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			this.gui.appendOutput(f1.getName() + " finished");
			try {
				bwl.write(f1.getName() + " finished\n");
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
		}
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
