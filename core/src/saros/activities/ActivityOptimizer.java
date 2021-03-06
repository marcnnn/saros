package saros.activities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import saros.filesystem.IFile;

/** Optimizer for activities. */
public class ActivityOptimizer {

  /**
   * Tries to reduce the number of {@link IActivity activities} so that:
   *
   * <p>
   *
   * <pre>
   * for (activity : optimize(activities))
   *         exec(activity)
   *
   * will produce the same result as
   *
   * for (activity : activities)
   *         exec(activity)
   * </pre>
   *
   * @param activities a collection containing the activities to optimize
   * @return a list which may contains a reduced amount of activities
   */
  public static List<IActivity> optimize(Collection<IActivity> activities) {

    List<IActivity> result = new ArrayList<>(activities.size());

    boolean[] dropActivityIdx = new boolean[activities.size()];

    Map<IFile, Integer> selections = new HashMap<>();
    Map<IFile, Integer> viewports = new HashMap<>();

    /*
     * keep only the latest selection/viewport activities per reference point and file
     */

    int activityIdx = 0;

    for (IActivity activity : activities) {

      if (activity instanceof TextSelectionActivity) {
        IFile file = ((TextSelectionActivity) activity).getResource();

        Integer idx = selections.get(file);

        if (idx != null) dropActivityIdx[idx] = true;

        selections.put(file, activityIdx);
      } else if (activity instanceof ViewportActivity) {
        IFile file = ((ViewportActivity) activity).getResource();

        Integer idx = viewports.get(file);

        if (idx != null) dropActivityIdx[idx] = true;

        viewports.put(file, activityIdx);
      }

      activityIdx++;
    }

    activityIdx = 0;

    for (IActivity activity : activities) if (!dropActivityIdx[activityIdx++]) result.add(activity);

    return result;
  }
}
