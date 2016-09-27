/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */
package gobblin.data.management.retention.action;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.FileSystem;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;

import gobblin.data.management.dataset.Dataset;
import gobblin.data.management.policy.VersionSelectionPolicy;
import gobblin.data.management.retention.dataset.ConfigurableCleanableDataset;
import gobblin.data.management.version.DatasetVersion;
import gobblin.data.management.version.finder.VersionFinder;
import gobblin.dataset.DatasetsFinder;
import gobblin.util.ClassAliasResolver;
import gobblin.util.ConfigUtils;
import gobblin.util.reflection.GobblinConstructorUtils;


/**
 * An abstraction to perform a retention action for a subset of {@link DatasetVersion}s.
 * A few kinds of actions are deletion, access control, encryption, archival etc.
 */
public abstract class RetentionAction {

  protected final FileSystem fs;
  @SuppressWarnings("rawtypes")
  protected final ClassAliasResolver<VersionSelectionPolicy> versionSelectionAliasResolver;

  public RetentionAction(Config actionConfig, FileSystem fs) {
    this.versionSelectionAliasResolver = new ClassAliasResolver<>(VersionSelectionPolicy.class);
    this.fs = fs;
  }

  /**
   * Execute the action on all {@link DatasetVersion}s or a subset of {@link DatasetVersion}s. Each {@link Dataset}
   * uses the {@link VersionFinder} to find all the {@link DatasetVersion}s and calls this method to perform the necessary
   * action on those {@link DatasetVersion}s
   * <p>
   * <b>Note</b> Any kind of {@link VersionSelectionPolicy} has <b>NOT</b> been applied to the list of {@link DatasetVersion}s
   * being passed. It is the responsibility of the {@link RetentionAction} to filter the {@link DatasetVersion}s by
   * applying {@link VersionSelectionPolicy}s and then perform the action.
   * </p>
   * @param allVersions list of all {@link DatasetVersion}s found by the {@link DatasetsFinder}.
   */
  public abstract void execute(List<DatasetVersion> allVersions) throws IOException;

  /**
   * A factory to create new {@link RetentionAction}s
   *
   */
  public interface RetentionActionFactory {

    /**
     * A factory method to create a new {@link RetentionAction} using a <code>config</code>. The {@link Dataset} always
     * calls {@link #canCreateWithConfig(Config)} before calling this method.
     *
     * @param config to use to create the {@link RetentionAction}
     * @return A new {@link RetentionAction}
     */
    RetentionAction createRetentionAction(Config config, FileSystem fs);

    /**
     * Method to check if a {@link RetentionAction} can be created/instantiated with the <code>config</code>.
     * If the specific type of {@link RetentionAction} has been specified in the configuration the method returns
     * <code>true</code>
     * If the method returns <code>true</code>, {@link #createRetentionAction(Config, FileSystem)} can be called to create
     * this {@link RetentionAction}.
     *
     * @param config to use to create the {@link RetentionAction}
     * @return true if the specific type of {@link RetentionAction} has been specified in the configuration, false otherwise
     */
    boolean canCreateWithConfig(Config config);
  }

  /*
   * Since {@link VersionSelectionPolicy} does not have a factory to create new objects we need to use the legacy
   * pattern of creating new objects using GobblinConstructorUtils
   */
  @SuppressWarnings("unchecked")
  protected VersionSelectionPolicy<DatasetVersion> createSelectionPolicy(Config selectionConfig) {
    try {
      String selectionPolicyKey =
          StringUtils.substringAfter(ConfigurableCleanableDataset.SELECTION_POLICY_CLASS_KEY,
              ConfigurableCleanableDataset.CONFIGURATION_KEY_PREFIX);
      Preconditions.checkArgument(selectionConfig.hasPath(selectionPolicyKey));
      String className = selectionConfig.getString(selectionPolicyKey);
      return (VersionSelectionPolicy<DatasetVersion>) GobblinConstructorUtils.invokeFirstConstructor(
          this.versionSelectionAliasResolver.resolveClass(className), ImmutableList.<Object> of(selectionConfig),
          ImmutableList.<Object> of(selectionConfig, ConfigUtils.configToProperties(selectionConfig)),
          ImmutableList.<Object> of(ConfigUtils.configToProperties(selectionConfig)));
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException
        | ClassNotFoundException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
