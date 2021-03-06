package iitb.CRF;

import java.util.ArrayList;
import java.util.Vector;

import iitb.CRF.Trainer.MultFunc;
import iitb.CRF.Trainer.MultSingle;
import cern.colt.function.DoubleFunction;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import java.lang.Math;

public class Marginals {

	public FeatureGenerator featureGenerator;
	public DataIter diter;
	public EdgeGenerator edgeGen;
	public double[] lambda;
	public int numY;
	public double[] scale;
	public double[] entropy;

	protected DoubleMatrix2D Mi_YY;
	protected DoubleMatrix1D Ri_Y;
	protected DoubleMatrix1D alpha_Y, newAlpha_Y;
	protected DoubleMatrix1D beta_Y[];
	protected DoubleMatrix1D tmp_Y;
	protected CrfParams params;
	protected DoubleMatrix1D alpha_temp[];
	protected DataSequence dataSeq;
	protected boolean reuseM, initMDone = false;

	static public Vector<DoubleMatrix1D> alpha;
	static public Vector<DoubleMatrix1D> beta;
	protected double Zx;
	public double[][] pr;
	public double[][] margPr;

	class MultSingle implements DoubleFunction {
		public double multiplicator = 1.0;

		public double apply(double a) {
			return a * multiplicator;
		}
	};

	static MultFunc multFunc = new MultFunc();
	MultSingle constMultiplier = new MultSingle();

	public Marginals(CRF model, DataSequence data, Evaluator eval) {
		this.params = model.params;
		dataSeq = data;
		alpha = new Vector();
		beta = new Vector();
		pr = new double[data.length()][model.numY];
		margPr = new double[data.length()][model.numY];
		entropy = new double[dataSeq.length()];
		init(model);
		initMatrices();
	}

	private void init(CRF model) {
		this.edgeGen = model.edgeGen;
		this.featureGenerator = model.featureGenerator;
		this.lambda = model.lambda;
		this.numY = model.numY;
		this.reuseM = false;
	}

	private void initMatrices() {
		Mi_YY = new DenseDoubleMatrix2D(numY, numY);
		Ri_Y = new DenseDoubleMatrix1D(numY);

		alpha_Y = new DenseDoubleMatrix1D(numY);
		newAlpha_Y = new DenseDoubleMatrix1D(numY);
		tmp_Y = new DenseDoubleMatrix1D(numY);

	}

	public void compute() {

		alpha_Y.assign(1);
		initMDone = false;
		boolean doScaling = params.doScaling;

		if ((beta_Y == null) || (beta_Y.length < dataSeq.length())) {
			beta_Y = new DenseDoubleMatrix1D[2 * dataSeq.length()];
			for (int i = 0; i < beta_Y.length; i++)
				beta_Y[i] = new DenseDoubleMatrix1D(numY);

			scale = new double[2 * dataSeq.length()];
			scale[dataSeq.length() - 1] = (doScaling) ? numY : 1;
			beta_Y[dataSeq.length() - 1]
					.assign(1.0 / scale[dataSeq.length() - 1]);
		}
		beta.add(beta_Y[dataSeq.length() - 1]);
		// System.out.println("Beta "+beta_Y[3].toString());
		for (int i = dataSeq.length() - 1; i > 0; i--) {
			if (params.debugLvl > 2) {
				Util.printDbg("Features fired");
				// featureGenerator.startScanFeaturesAt(dataSeq, i);
				// while (featureGenerator.hasNext()) {
				// Feature feature = featureGenerator.next();
				// Util.printDbg(feature.toString());
				// }
			}

			// compute the Mi matrix
			// System.out.println("MI previous" +Mi_YY.toString());
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI "+Mi_YY.toString());
			tmp_Y.assign(beta_Y[i]);
			tmp_Y.assign(Ri_Y, multFunc);
			RobustMath.Mult(Mi_YY, tmp_Y, beta_Y[i - 1], 1, 0, false, edgeGen);

			// need to scale the beta-s to avoid overflow
			scale[i - 1] = doScaling ? beta_Y[i - 1].zSum() : 1;
			if ((scale[i - 1] < 1) && (scale[i - 1] > -1))
				scale[i - 1] = 1;
			constMultiplier.multiplicator = 1.0 / scale[i - 1];
			beta_Y[i - 1].assign(constMultiplier);
			// System.out.println("Beta "+beta_Y[i - 1].toString() + " ");
			beta.add(beta_Y[i - 1]);
		}

		double thisSeqLogli = 0;
		//System.out.println("\n");
		// Mi_YY.assign(0);
		alpha_temp = new DenseDoubleMatrix1D[2 * dataSeq.length()];
		for (int i = 0; i < dataSeq.length(); i++)
			alpha_temp[i] = new DenseDoubleMatrix1D(numY);

		for (int i = 0; i < dataSeq.length(); i++) {
			// compute the Mi matrix
			//
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI: " + Mi_YY.toString());
			// find features that fire at this position..
			featureGenerator.startScanFeaturesAt(dataSeq, i);

			if (i > 0) {
				tmp_Y.assign(alpha_Y);
				RobustMath.Mult(Mi_YY, tmp_Y, newAlpha_Y, 1, 0, true, edgeGen);
				// Mi_YY.zMult(tmp_Y, newAlpha_Y,1,0,true);
				newAlpha_Y.assign(Ri_Y, multFunc);
			} else {
				newAlpha_Y.assign(Ri_Y);
			}
			while (featureGenerator.hasNext()) {
				Feature feature = featureGenerator.next();
				int f = feature.index();

				int yp = feature.y();
				int yprev = feature.yprev();
				float val = feature.value();
				if ((dataSeq.y(i) == yp)
						&& (((i - 1 >= 0) && (yprev == dataSeq.y(i - 1))) || (yprev < 0))) {

					thisSeqLogli += val * lambda[f];
				}

			}

			alpha_Y.assign(newAlpha_Y);
			// now scale the alpha-s to avoid overflow problems.
			constMultiplier.multiplicator = 1.0 / scale[i];
			alpha_Y.assign(constMultiplier);
			alpha_temp[i].assign(newAlpha_Y);
			alpha_temp[i].assign(constMultiplier);
			// System.out.println("ALpha "+alpha_Y.toString());
			alpha.add(alpha_temp[i]);
			if (params.debugLvl > 2) {
				//System.out.println("Alpha-i " + alpha_Y.toString());
				//System.out.println("Ri " + Ri_Y.toString());
				//System.out.println("Mi " + Mi_YY.toString());
				//System.out.println("Beta-i " + beta_Y[i].toString());
			}
		}

		Zx = alpha_Y.zSum();
		//System.out.println("Zx: " + Zx);

	} /* end of computeBeta */
	
	//
	
	public void compute(int pos, int label) {

		alpha_Y.assign(1);
		initMDone = false;
		boolean doScaling = params.doScaling;

		if ((beta_Y == null) || (beta_Y.length < dataSeq.length())) {
			beta_Y = new DenseDoubleMatrix1D[2 * dataSeq.length()];
			for (int i = 0; i < beta_Y.length; i++)
				beta_Y[i] = new DenseDoubleMatrix1D(numY);

			scale = new double[2 * dataSeq.length()];
			scale[dataSeq.length() - 1] = (doScaling) ? numY : 1;
			beta_Y[dataSeq.length() - 1]
					.assign(1.0 / scale[dataSeq.length() - 1]);
		}
		if (pos==dataSeq.length()-1) {
			for(int k=0;k<beta_Y[pos].size();k++) {
				if (k!=label)
				beta_Y[pos].set(k, 0);
			}
		}
			//beta_Y[dataSeq.length()-1].setQuick(label, 0);
		beta.add(beta_Y[dataSeq.length() - 1]);
		// System.out.println("Beta "+beta_Y[3].toString());
		for (int i = dataSeq.length() - 1; i > 0; i--) {
			if (params.debugLvl > 2) {
				Util.printDbg("Features fired");
				// featureGenerator.startScanFeaturesAt(dataSeq, i);
				// while (featureGenerator.hasNext()) {
				// Feature feature = featureGenerator.next();
				// Util.printDbg(feature.toString());
				// }
			}

			// compute the Mi matrix
			// System.out.println("MI previous" +Mi_YY.toString());
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI "+Mi_YY.toString());
			tmp_Y.assign(beta_Y[i]);
			tmp_Y.assign(Ri_Y, multFunc);
			RobustMath.Mult(Mi_YY, tmp_Y, beta_Y[i - 1], 1, 0, false, edgeGen);

			// need to scale the beta-s to avoid overflow
			scale[i - 1] = doScaling ? beta_Y[i - 1].zSum() : 1;
			if ((scale[i - 1] < 1) && (scale[i - 1] > -1))
				scale[i - 1] = 1;
			constMultiplier.multiplicator = 1.0 / scale[i - 1];
			beta_Y[i - 1].assign(constMultiplier);
			// System.out.println("Beta "+beta_Y[i - 1].toString() + " ");
			if (i-1==pos) {
				for(int k=0;k<beta_Y[i-1].size();k++) {
					if (k!=label)
					beta_Y[i-1].set(k, 0);
				}
			}
			beta.add(beta_Y[i - 1]);
		}

		double thisSeqLogli = 0;
		////System.out.println("\n");
		// Mi_YY.assign(0);
		alpha_temp = new DenseDoubleMatrix1D[2 * dataSeq.length()];
		for (int i = 0; i < dataSeq.length(); i++)
			alpha_temp[i] = new DenseDoubleMatrix1D(numY);

		for (int i = 0; i < dataSeq.length(); i++) {
			// compute the Mi matrix
			//
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI: " + Mi_YY.toString());
			// find features that fire at this position..
			featureGenerator.startScanFeaturesAt(dataSeq, i);

			if (i > 0) {
				tmp_Y.assign(alpha_Y);
				RobustMath.Mult(Mi_YY, tmp_Y, newAlpha_Y, 1, 0, true, edgeGen);
				// Mi_YY.zMult(tmp_Y, newAlpha_Y,1,0,true);
				newAlpha_Y.assign(Ri_Y, multFunc);
			} else {
				newAlpha_Y.assign(Ri_Y);
			}
			while (featureGenerator.hasNext()) {
				Feature feature = featureGenerator.next();
				int f = feature.index();

				int yp = feature.y();
				int yprev = feature.yprev();
				float val = feature.value();
				if ((dataSeq.y(i) == yp)
						&& (((i - 1 >= 0) && (yprev == dataSeq.y(i - 1))) || (yprev < 0))) {

					thisSeqLogli += val * lambda[f];
				}

			}

			alpha_Y.assign(newAlpha_Y);
			// now scale the alpha-s to avoid overflow problems.
			constMultiplier.multiplicator = 1.0 / scale[i];
			alpha_Y.assign(constMultiplier);
			alpha_temp[i].assign(newAlpha_Y);
			alpha_temp[i].assign(constMultiplier);
			// System.out.println("ALpha "+alpha_Y.toString());
			/* constrained forward */
			if (i==pos) {
				for(int k=0;k<alpha_temp[i].size();k++) {
					if (k!=label)
					alpha_temp[i].set(k, 0);
				}	
			}
				
			alpha.add(alpha_temp[i]);
			if (params.debugLvl > 2) {
				//System.out.println("Alpha-i " + alpha_Y.toString());
				//System.out.println("Ri " + Ri_Y.toString());
				//System.out.println("Mi " + Mi_YY.toString());
				//System.out.println("Beta-i " + beta_Y[i].toString());
			}
		}

		Zx = alpha_Y.zSum();
		//System.out.println("Zx: " + Zx+" "+alpha.size()+" "+beta.size());

	}
	
	public void compute(int pos, int label, int labelLeft, int labelRight) {

		alpha_Y.assign(1);
		initMDone = false;
		boolean doScaling = params.doScaling;

		if ((beta_Y == null) || (beta_Y.length < dataSeq.length())) {
			beta_Y = new DenseDoubleMatrix1D[2 * dataSeq.length()];
			for (int i = 0; i < beta_Y.length; i++)
				beta_Y[i] = new DenseDoubleMatrix1D(numY);

			scale = new double[2 * dataSeq.length()];
			scale[dataSeq.length() - 1] = (doScaling) ? numY : 1;
			beta_Y[dataSeq.length() - 1]
					.assign(1.0 / scale[dataSeq.length() - 1]);
		}
		if (pos==dataSeq.length()-1) {
			for(int k=0;k<beta_Y[pos].size();k++) {
				if (k!=label)
				beta_Y[pos].set(k, 0);
			}
			for(int k=0;k<beta_Y[pos-1].size();k++) {
				if (k!=labelLeft)
				beta_Y[pos-1].set(k, 0);
			}	
		}
			//beta_Y[dataSeq.length()-1].setQuick(label, 0);
		beta.add(beta_Y[dataSeq.length() - 1]);
		// System.out.println("Beta "+beta_Y[3].toString());
		for (int i = dataSeq.length() - 1; i > 0; i--) {
			if (params.debugLvl > 2) {
				Util.printDbg("Features fired");
				// featureGenerator.startScanFeaturesAt(dataSeq, i);
				// while (featureGenerator.hasNext()) {
				// Feature feature = featureGenerator.next();
				// Util.printDbg(feature.toString());
				// }
			}

			// compute the Mi matrix
			// System.out.println("MI previous" +Mi_YY.toString());
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI "+Mi_YY.toString());
			tmp_Y.assign(beta_Y[i]);
			tmp_Y.assign(Ri_Y, multFunc);
			RobustMath.Mult(Mi_YY, tmp_Y, beta_Y[i - 1], 1, 0, false, edgeGen);

			// need to scale the beta-s to avoid overflow
			scale[i - 1] = doScaling ? beta_Y[i - 1].zSum() : 1;
			if ((scale[i - 1] < 1) && (scale[i - 1] > -1))
				scale[i - 1] = 1;
			constMultiplier.multiplicator = 1.0 / scale[i - 1];
			beta_Y[i - 1].assign(constMultiplier);
			// System.out.println("Beta "+beta_Y[i - 1].toString() + " ");
			if (i-1==pos) {
				for(int k=0;k<beta_Y[i-1].size();k++) {
					if (k!=label)
					beta_Y[i-1].set(k, 0);
				}
				for(int k=0;k<beta_Y[i-2].size();k++) {
					if (k!=labelLeft)
					beta_Y[i-2].set(k, 0);
				}
				for(int k=0;k<beta_Y[i].size();k++) {
					if (k!=labelRight)
					beta_Y[i].set(k, 0);
				}	
			}
			beta.add(beta_Y[i - 1]);
		}

		double thisSeqLogli = 0;
		////System.out.println("\n");
		// Mi_YY.assign(0);
		alpha_temp = new DenseDoubleMatrix1D[2 * dataSeq.length()];
		for (int i = 0; i < dataSeq.length(); i++)
			alpha_temp[i] = new DenseDoubleMatrix1D(numY);

		for (int i = 0; i < dataSeq.length(); i++) {
			// compute the Mi matrix
			//
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI: " + Mi_YY.toString());
			// find features that fire at this position..
			featureGenerator.startScanFeaturesAt(dataSeq, i);

			if (i > 0) {
				tmp_Y.assign(alpha_Y);
				RobustMath.Mult(Mi_YY, tmp_Y, newAlpha_Y, 1, 0, true, edgeGen);
				// Mi_YY.zMult(tmp_Y, newAlpha_Y,1,0,true);
				newAlpha_Y.assign(Ri_Y, multFunc);
			} else {
				newAlpha_Y.assign(Ri_Y);
			}
			while (featureGenerator.hasNext()) {
				Feature feature = featureGenerator.next();
				int f = feature.index();

				int yp = feature.y();
				int yprev = feature.yprev();
				float val = feature.value();
				if ((dataSeq.y(i) == yp)
						&& (((i - 1 >= 0) && (yprev == dataSeq.y(i - 1))) || (yprev < 0))) {

					thisSeqLogli += val * lambda[f];
				}

			}

			alpha_Y.assign(newAlpha_Y);
			// now scale the alpha-s to avoid overflow problems.
			constMultiplier.multiplicator = 1.0 / scale[i];
			alpha_Y.assign(constMultiplier);
			alpha_temp[i].assign(newAlpha_Y);
			alpha_temp[i].assign(constMultiplier);
			// System.out.println("ALpha "+alpha_Y.toString());
			/* constrained forward */
			if (i==pos) {
				for(int k=0;k<alpha_temp[i].size();k++) {
					if (k!=label)
					alpha_temp[i].set(k, 0);
				}	
			}
			if (i>1 && i==(pos-1)) {
				for(int k=0;k<alpha_temp[i].size();k++) {
					if (k!=labelLeft)
					alpha_temp[i].set(k, 0);
				}	
			}
			if (i<dataSeq.length() && i==(pos+1)) {
				for(int k=0;k<alpha_temp[i].size();k++) {
					if (k!=labelRight)
					alpha_temp[i].set(k, 0);
				}	
			}
				
			alpha.add(alpha_temp[i]);
			if (params.debugLvl > 2) {
				//System.out.println("Alpha-i " + alpha_Y.toString());
				//System.out.println("Ri " + Ri_Y.toString());
				//System.out.println("Mi " + Mi_YY.toString());
				//System.out.println("Beta-i " + beta_Y[i].toString());
			}
		}

		Zx = alpha_Y.zSum();
		//System.out.println("Zx: " + Zx+" "+alpha.size()+" "+beta.size());

	}
	
	public void compute(int pos, int label, int labelLeft) {

		alpha_Y.assign(1);
		initMDone = false;
		boolean doScaling = params.doScaling;

		if ((beta_Y == null) || (beta_Y.length < dataSeq.length())) {
			beta_Y = new DenseDoubleMatrix1D[2 * dataSeq.length()];
			for (int i = 0; i < beta_Y.length; i++)
				beta_Y[i] = new DenseDoubleMatrix1D(numY);

			scale = new double[2 * dataSeq.length()];
			scale[dataSeq.length() - 1] = (doScaling) ? numY : 1;
			beta_Y[dataSeq.length() - 1]
					.assign(1.0 / scale[dataSeq.length() - 1]);
		}
		if (pos==dataSeq.length()-1) {
			for(int k=0;k<beta_Y[pos].size();k++) {
				if (k!=label)
				beta_Y[pos].set(k, 0);
			}
			for(int k=0;k<beta_Y[pos-1].size();k++) {
				if (k!=labelLeft)
				beta_Y[pos-1].set(k, 0);
			}	
		}
			//beta_Y[dataSeq.length()-1].setQuick(label, 0);
		beta.add(beta_Y[dataSeq.length() - 1]);
		// System.out.println("Beta "+beta_Y[3].toString());
		for (int i = dataSeq.length() - 1; i > 0; i--) {
			if (params.debugLvl > 2) {
				Util.printDbg("Features fired");
				// featureGenerator.startScanFeaturesAt(dataSeq, i);
				// while (featureGenerator.hasNext()) {
				// Feature feature = featureGenerator.next();
				// Util.printDbg(feature.toString());
				// }
			}

			// compute the Mi matrix
			// System.out.println("MI previous" +Mi_YY.toString());
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI "+Mi_YY.toString());
			tmp_Y.assign(beta_Y[i]);
			tmp_Y.assign(Ri_Y, multFunc);
			RobustMath.Mult(Mi_YY, tmp_Y, beta_Y[i - 1], 1, 0, false, edgeGen);

			// need to scale the beta-s to avoid overflow
			scale[i - 1] = doScaling ? beta_Y[i - 1].zSum() : 1;
			if ((scale[i - 1] < 1) && (scale[i - 1] > -1))
				scale[i - 1] = 1;
			constMultiplier.multiplicator = 1.0 / scale[i - 1];
			beta_Y[i - 1].assign(constMultiplier);
			// System.out.println("Beta "+beta_Y[i - 1].toString() + " ");
			if (i-1==pos) {
				for(int k=0;k<beta_Y[i-1].size();k++) {
					if (k!=label)
					beta_Y[i-1].set(k, 0);
				}
				for(int k=0;k<beta_Y[i-2].size();k++) {
					if (k!=labelLeft)
					beta_Y[i-2].set(k, 0);
				}	
			}
			beta.add(beta_Y[i - 1]);
		}

		double thisSeqLogli = 0;
		////System.out.println("\n");
		// Mi_YY.assign(0);
		alpha_temp = new DenseDoubleMatrix1D[2 * dataSeq.length()];
		for (int i = 0; i < dataSeq.length(); i++)
			alpha_temp[i] = new DenseDoubleMatrix1D(numY);

		for (int i = 0; i < dataSeq.length(); i++) {
			// compute the Mi matrix
			//
			initMDone = Trainer.computeLogMi(featureGenerator, lambda, dataSeq,
					i, Mi_YY, Ri_Y, true, reuseM, initMDone);
			// System.out.println("MI: " + Mi_YY.toString());
			// find features that fire at this position..
			featureGenerator.startScanFeaturesAt(dataSeq, i);

			if (i > 0) {
				tmp_Y.assign(alpha_Y);
				RobustMath.Mult(Mi_YY, tmp_Y, newAlpha_Y, 1, 0, true, edgeGen);
				// Mi_YY.zMult(tmp_Y, newAlpha_Y,1,0,true);
				newAlpha_Y.assign(Ri_Y, multFunc);
			} else {
				newAlpha_Y.assign(Ri_Y);
			}
			while (featureGenerator.hasNext()) {
				Feature feature = featureGenerator.next();
				int f = feature.index();

				int yp = feature.y();
				int yprev = feature.yprev();
				float val = feature.value();
				if ((dataSeq.y(i) == yp)
						&& (((i - 1 >= 0) && (yprev == dataSeq.y(i - 1))) || (yprev < 0))) {

					thisSeqLogli += val * lambda[f];
				}

			}

			alpha_Y.assign(newAlpha_Y);
			// now scale the alpha-s to avoid overflow problems.
			constMultiplier.multiplicator = 1.0 / scale[i];
			alpha_Y.assign(constMultiplier);
			alpha_temp[i].assign(newAlpha_Y);
			alpha_temp[i].assign(constMultiplier);
			// System.out.println("ALpha "+alpha_Y.toString());
			/* constrained forward */
			if (i==pos) {
				for(int k=0;k<alpha_temp[i].size();k++) {
					if (k!=label)
					alpha_temp[i].set(k, 0);
				}	
			}
			if (i>1 && i==(pos-1)) {
				for(int k=0;k<alpha_temp[i].size();k++) {
					if (k!=labelLeft)
					alpha_temp[i].set(k, 0);
				}	
			}
				
			alpha.add(alpha_temp[i]);
			if (params.debugLvl > 2) {
				//System.out.println("Alpha-i " + alpha_Y.toString());
				//System.out.println("Ri " + Ri_Y.toString());
				//System.out.println("Mi " + Mi_YY.toString());
				//System.out.println("Beta-i " + beta_Y[i].toString());
			}
		}

		Zx = alpha_Y.zSum();
		//System.out.println("Zx: " + Zx+" "+alpha.size()+" "+beta.size());

	} 

	public void printAlphaVector() {
		//System.out.println("ALPHA");
		for (DoubleMatrix1D m : alpha) {
			//System.out.println(m.toString());
		}
	}

	public void printBetaVector() {
		//System.out.println("BETA");
		for (DoubleMatrix1D m : beta) {
			//System.out.println(m.toString());
		}
	}

	public void computeMarginal() {
		for (int i = 0; i < dataSeq.length(); i++) {
			for (int j = 0; j < numY; j++) {
				pr[i][j] = (alpha.get(i).get(j) * beta.get(
						dataSeq.length() - i - 1).get(j))
						/ Zx;
				//System.out.print(pr[i][j] + " ");
			}
			////System.out.print("\n");
		}
	}

	public void normalize() {
		for (int i = 0; i < dataSeq.length(); i++) {
			double rowSum = 0;
			for (int j = 0; j < numY; j++) {
				rowSum += pr[i][j];
			}
			for (int j = 0; j < numY; j++) {
				margPr[i][j] = pr[i][j] / rowSum;
			}
		}
	}

	public void printNormalized() {
		for (int i = 0; i < dataSeq.length(); i++) {
			//for (int j = 0; j < numY; j++)
				//System.out.print(margPr[i][j] + " | ");
			//System.out.print("\n");
		}
	}

	public void calculateEntropy(int flag) {
		for (int i = 0; i < dataSeq.length(); i++) {
			for (int j = 0; j < numY; j++) {
				if (flag==0) {
					// Original Method
					entropy[i] += -margPr[i][j] * Math.log(margPr[i][j]);
				}
				else {
					////System.out.println("i: " + i + ", j: " + j);
					// Neighborhoods Method
					if (i==0) {
						//System.out.println("margPr: " + margPr[i][j]);
						//System.out.println("margPr2: " + margPr[i+1][j]);
						entropy[i] += -margPr[i][j] * Math.log(margPr[i][j])
									+ -margPr[i+1][j] * Math.log(margPr[i+1][j]);
					}
					else if (i==dataSeq.length()-1) {
						entropy[i] += -margPr[i-1][j] * Math.log(margPr[i-1][j])
						+ -margPr[i][j] * Math.log(margPr[i][j]);
					}
					else {
					entropy[i] += -margPr[i-1][j] * Math.log(margPr[i-1][j])
								+ -margPr[i][j] * Math.log(margPr[i][j])
								+ -margPr[i+1][j] * Math.log(margPr[i+1][j]);
					}
				}
					//if (i==58)
					//System.out.print("marginal "+i+" "+margPr[i][j]+" "+Math.log(margPr[i][j])+" | ");
			}
		}
	}
	
	public void calculateEntropy(int pos, int flag) {
		
		for (int i = 0; i < dataSeq.length(); i++) {
			if (i==pos || i==(pos-1) || (i==(pos+1))) {
				entropy[i]=0;
				continue;
			}
			for (int j = 0; j < numY; j++) {
				if (flag==0) {
					// Original Method
					entropy[i] += -margPr[i][j] * Math.log(margPr[i][j]);
				}
				else {
					// Neighborhoods Method
					if (i==0) {
						entropy[i] = -margPr[i][j] * Math.log(margPr[i][j])
									+ -margPr[i+1][j] * Math.log(margPr[i+1][j]);
					}
					else if (i==dataSeq.length()-1) {
						entropy[i] += -margPr[i-1][j] * Math.log(margPr[i-1][j])
						+ -margPr[i][j] * Math.log(margPr[i][j]);
					}
					else {
					entropy[i] += -margPr[i-1][j] * Math.log(margPr[i-1][j])
								+ -margPr[i][j] * Math.log(margPr[i][j])
								+ -margPr[i+1][j] * Math.log(margPr[i+1][j]);
					}
				}
					//if (i==58)
					//System.out.print("marginal "+i+" "+margPr[i][j]+" "+Math.log(margPr[i][j])+" | ");
			}
		}	
	}	

	public void printEntropy() {
		for (double d : entropy)
			System.out.println(d);
		//System.out.println("Total Entropies : " + dataSeq.length());
	}
	
	public int getMaxEntropyNode() {
		int maxIndex=0;
		double max = entropy[0];
		for(int i=1;i<entropy.length;i++) {
			if(max<entropy[i]) {
				max=entropy[i];
				maxIndex=i;
			}
		}
		return maxIndex;
	}
	
	public double averageEntropy() {
		double sum=0;
		for(double d:entropy)
			sum=sum+d;
		//System.out.println("Avg: "+sum+" "+entropy.length+" "+sum/entropy.length);
		return sum/entropy.length;
	}

	public double getEntropy(int d) {
		return(entropy[d]);
	}
	
	public Double[] top3Entropy() {
		Double[] top3 = new Double[3];
		int maxIndex = 0;
		Double max = entropy[0];
		for(int i=1; i<entropy.length;i++) {
			if (max<entropy[i]) {
				max=entropy[i];
				maxIndex=i;
			}
		}
		if (maxIndex==1) {
			top3[0] = 0.0;
		    top3[1] = entropy[maxIndex];
		    top3[2] = entropy[maxIndex+1];
		}
		else if (maxIndex==entropy.length-1) {
			if (entropy.length==1) {
				top3[0] = 0.0;
				top3[1] = entropy[maxIndex];
				top3[2] = 0.0;
			}
			else {
			top3[0] = entropy[maxIndex-1];
			top3[1] = entropy[maxIndex];
			top3[2] = 0.0;
			}
		}
		else {
			//System.out.println(Double.toString(maxIndex) + " ");
			//System.out.println(Double.toString(entropy.length));
			top3[0] = entropy[maxIndex-1];
			System.out.println("1");
			top3[1] = entropy[maxIndex];
			System.out.println("2");
			top3[2] = entropy[maxIndex+1];
			System.out.println("3");
		}
		return top3;
	}

	public int getNumMaxEntropyNodes(double Thresh) {
			int count = 0;
			for (int i=1; i<entropy.length;i++){
				if (entropy[i] > Thresh)
					count++;
			}
			return count;
	}

        public Double[] getMarginalProb(int pos) {
            Double[] margSeq = new Double[numY];
            for (int i=0; i<numY; i++) {
                margSeq[i] = margPr[pos][i];
            }
                
            return margSeq;
        }
}


	
