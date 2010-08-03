package de.lmu.ifi.dbs.elki.algorithm.outlier;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.EM;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.EMModel;
import de.lmu.ifi.dbs.elki.database.AssociationID;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.result.AnnotationFromDataStore;
import de.lmu.ifi.dbs.elki.result.AnnotationResult;
import de.lmu.ifi.dbs.elki.result.OrderingFromDataStore;
import de.lmu.ifi.dbs.elki.result.OrderingResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.ProbabilisticOutlierScore;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.EmptyParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;

/**
 * outlier detection algorithm using EM Clustering. If an object does not belong
 * to any cluster it is supposed to be an outlier. If the probability for an
 * object to belong to the most probable cluster is still relatively low this
 * object is an outlier.
 * 
 * @author Lisa Reichert
 * 
 * @param <V> Vector type
 */
@Title("EM Outlier: Outlier Detection based on the generic EM clustering")
@Description("The outlier score assigned is based on the highest cluster probability obtained from EM clustering.")
public class EMOutlier<V extends NumberVector<V, ?>> extends AbstractAlgorithm<V, OutlierResult> {
  /**
   * Inner algorithm.
   */
  private EM<V> emClustering;

  /**
   * Association id to associate
   */
  public static final AssociationID<Double> EMOD_MAXCPROB = AssociationID.getOrCreateAssociationID("emod_maxcprob", Double.class);

  /**
   * Constructor with an existing em clustering algorithm.
   * 
   * @param emClustering EM clustering algorithm to use.
   */
  public EMOutlier(EM<V> emClustering) {
    super(new EmptyParameterization());
    this.emClustering = emClustering;
  }

  /**
   * Runs the algorithm in the timed evaluation part.
   */
  @Override
  protected OutlierResult runInTime(Database<V> database) throws IllegalStateException {
    Clustering<EMModel<V>> emresult = emClustering.run(database);

    double globmax = 0.0;
    WritableDataStore<Double> emo_score = DataStoreUtil.makeStorage(database.getIDs(), DataStoreFactory.HINT_TEMP | DataStoreFactory.HINT_HOT, Double.class);
    for(DBID id : database) {
      double maxProb = Double.POSITIVE_INFINITY;
      double[] probs = emClustering.getProbClusterIGivenX(id);
      for(double prob : probs) {
        maxProb = Math.min(1 - prob, maxProb);
      }
      emo_score.put(id, maxProb);
      globmax = Math.max(maxProb, globmax);
    }
    AnnotationResult<Double> scoreres = new AnnotationFromDataStore<Double>(EMOD_MAXCPROB, emo_score);
    OrderingResult ores = new OrderingFromDataStore<Double>(emo_score, true);
    OutlierScoreMeta meta = new ProbabilisticOutlierScore(0.0, globmax);
    // combine results.
    OutlierResult result = new OutlierResult(meta, scoreres, ores);
    result.addResult(emresult);
    return result;
  }

  /**
   * Factory method for {@link Parameterizable}
   * 
   * @param config Parameterization
   * @return EM Outlier detection algorithm
   */
  public static <V extends NumberVector<V, ?>> EMOutlier<V> parameterize(Parameterization config) {
    EM<V> emClustering = new EM<V>(config);
    if(config.hasErrors()) {
      return null;
    }
    return new EMOutlier<V>(emClustering);
  }
}