/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2018
 * ELKI Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.lmu.ifi.dbs.elki.utilities.scaling.outlier;

import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.relation.DoubleRelation;
import de.lmu.ifi.dbs.elki.math.MathUtil;
import de.lmu.ifi.dbs.elki.math.Mean;
import de.lmu.ifi.dbs.elki.math.MeanVarianceMinMax;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.NormalDistribution;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.NumberArrayAdapter;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;

import net.jafama.FastMath;

/**
 * Scaling that can map arbitrary values to a probability in the range of [0:1].
 * <p>
 * Transformation is done using the formulas
 * \[y = \sqrt{x - \min}\]
 * \[s = \max\{0, \textrm{erf}(\lambda \frac{y-\mu}{\sigma\sqrt{2}})\}\]
 * <p>
 * Where min and mean \(\mu\) can be fixed to a given value, and stddev
 * \(\sigma\) is then computed against this mean.
 * <p>
 * Reference:
 * <p>
 * Hans-Peter Kriegel, Peer Kröger, Erich Schubert, Arthur Zimek<br>
 * Interpreting and Unifying Outlier Scores<br>
 * Proc. 11th SIAM International Conference on Data Mining (SDM 2011)
 * 
 * @author Erich Schubert
 * @since 0.3
 */
@Reference(authors = "Hans-Peter Kriegel, Peer Kröger, Erich Schubert, Arthur Zimek", //
    title = "Interpreting and Unifying Outlier Scores", //
    booktitle = "Proc. 11th SIAM International Conference on Data Mining (SDM 2011)", //
    url = "https://doi.org/10.1137/1.9781611972818.2", //
    bibkey = "DBLP:conf/sdm/KriegelKSZ11")
public class SqrtStandardDeviationScaling implements OutlierScaling {
  /**
   * Effective parameters.
   */
  double min, mean, factor;

  /**
   * Pre-fixed minimum and mean.
   */
  double pmin = Double.NaN, pmean = Double.NaN;

  /**
   * Predefined lambda scaling factor.
   */
  double plambda;

  /**
   * Constructor.
   * 
   * @param pmin Predefined minimum
   * @param pmean Predefined mean
   * @param plambda Lambda parameter
   */
  public SqrtStandardDeviationScaling(double pmin, double pmean, double plambda) {
    super();
    this.pmin = pmin;
    this.pmean = pmean;
    this.plambda = plambda;
  }

  @Override
  public double getScaled(double value) {
    assert (factor != 0) : "prepare() was not run prior to using the scaling function.";
    if(value <= min) {
      return 0;
    }
    value = FastMath.sqrt(value - min);
    return value <= mean ? 0 : Math.max(0, NormalDistribution.erf((value - mean) / factor));
  }

  @Override
  public void prepare(OutlierResult or) {
    if(Double.isNaN(pmean)) {
      MeanVarianceMinMax mv = new MeanVarianceMinMax();
      DoubleRelation scores = or.getScores();
      for(DBIDIter id = scores.iterDBIDs(); id.valid(); id.advance()) {
        double val = scores.doubleValue(id);
        val = (val <= min) ? 0 : FastMath.sqrt(val - min);
        mv.put(val);
      }
      min = Double.isNaN(pmin) ? mv.getMin() : pmin;
      mean = mv.getMean();
      factor = plambda * mv.getSampleStddev() * MathUtil.SQRT2;
    }
    else {
      mean = pmean;
      double sqsum = 0;
      int cnt = 0;
      DoubleRelation scores = or.getScores();
      double mm = Double.POSITIVE_INFINITY;
      for(DBIDIter id = scores.iterDBIDs(); id.valid(); id.advance()) {
        double val = scores.doubleValue(id);
        mm = Math.min(mm, val);
        val = (val <= min) ? 0 : FastMath.sqrt(val - min);
        sqsum += (val - mean) * (val - mean);
        cnt += 1;
      }
      min = Double.isNaN(pmin) ? mm : pmin;
      factor = plambda * FastMath.sqrt(sqsum / cnt) * MathUtil.SQRT2;
    }
  }

  @Override
  public <A> void prepare(A array, NumberArrayAdapter<?, A> adapter) {
    if(Double.isNaN(pmean)) {
      MeanVarianceMinMax mv = new MeanVarianceMinMax();
      final int size = adapter.size(array);
      for(int i = 0; i < size; i++) {
        double val = adapter.getDouble(array, i);
        val = (val <= min) ? 0 : FastMath.sqrt(val - min);
        mv.put(val);
      }
      min = Double.isNaN(pmin) ? mv.getMin() : pmin;
      mean = mv.getMean();
      factor = plambda * mv.getSampleStddev() * MathUtil.SQRT2;
    }
    else {
      mean = pmean;
      Mean sqsum = new Mean();
      double mm = Double.POSITIVE_INFINITY;
      final int size = adapter.size(array);
      for(int i = 0; i < size; i++) {
        double val = adapter.getDouble(array, i);
        mm = Math.min(mm, val);
        val = (val <= min) ? 0 : FastMath.sqrt(val - min);
        sqsum.put((val - mean) * (val - mean));
      }
      min = Double.isNaN(pmin) ? mm : pmin;
      factor = plambda * FastMath.sqrt(sqsum.getMean()) * MathUtil.SQRT2;
    }
  }

  @Override
  public double getMin() {
    return 0.0;
  }

  @Override
  public double getMax() {
    return 1.0;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Parameter to specify the fixed minimum to use.
     */
    public static final OptionID MIN_ID = new OptionID("sqrtstddevscale.min", "Fixed minimum to use in sqrt scaling.");

    /**
     * Parameter to specify a fixed mean to use.
     */
    public static final OptionID MEAN_ID = new OptionID("sqrtstddevscale.mean", "Fixed mean to use in standard deviation scaling.");

    /**
     * Parameter to specify the lambda value
     */
    public static final OptionID LAMBDA_ID = new OptionID("sqrtstddevscale.lambda", "Significance level to use for error function.");

    /**
     * Pre-fixed minimum and mean.
     */
    double min = Double.NaN, mean = Double.NaN;

    /**
     * Predefined lambda scaling factor.
     */
    protected double lambda;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      DoubleParameter minP = new DoubleParameter(MIN_ID) //
          .setOptional(true);
      if(config.grab(minP)) {
        min = minP.doubleValue();
      }

      DoubleParameter meanP = new DoubleParameter(MEAN_ID) //
          .setOptional(true);
      if(config.grab(meanP)) {
        mean = meanP.doubleValue();
      }

      DoubleParameter lambdaP = new DoubleParameter(LAMBDA_ID, 3.0);
      if(config.grab(lambdaP)) {
        lambda = lambdaP.doubleValue();
      }
    }

    @Override
    protected SqrtStandardDeviationScaling makeInstance() {
      return new SqrtStandardDeviationScaling(min, mean, lambda);
    }
  }
}
