/**
 *
 *    Copyright 2016-2017, Optimizely and contributors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.optimizely.ab.config;

import com.optimizely.ab.faultinjection.ExceptionSpot;
import com.optimizely.ab.faultinjection.FaultInjectionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectConfigUtils {

    private static void injectFault(ExceptionSpot spot) {
        FaultInjectionManager.getInstance().injectFault(spot);
    }

    /**
     * Helper method for creating convenience mappings from key to entity
     */
    public static <T extends IdKeyMapped> Map<String, T> generateNameMapping(List<T> nameables) {
        try {
            injectFault(ExceptionSpot.ProjectConfigUtils_generateNameMapping_spot1);
            Map<String, T> nameMapping = new HashMap<String, T>();
            for (T nameable : nameables) {
                nameMapping.put(nameable.getKey(), nameable);
            }

            return Collections.unmodifiableMap(nameMapping);
        } catch (Exception e) {
            FaultInjectionManager.getInstance().throwExceptionIfTreatmentDisabled();
            return null;
        }
    }

    /**
     * Helper method for creating convenience mappings from ID to entity
     */
    public static <T extends IdMapped> Map<String, T> generateIdMapping(List<T> nameables) {
        try {
            injectFault(ExceptionSpot.ProjectConfigUtils_generateIdMapping_spot1);
            Map<String, T> nameMapping = new HashMap<String, T>();
            for (T nameable : nameables) {
                nameMapping.put(nameable.getId(), nameable);
            }

            return Collections.unmodifiableMap(nameMapping);
        } catch (Exception e) {
            FaultInjectionManager.getInstance().throwExceptionIfTreatmentDisabled();
            return null;
        }
    }

    /**
     * Helper method to create a map from a live variable to all the experiments using it
     */
    public static Map<String, List<Experiment>> generateLiveVariableIdToExperimentsMapping(
            List<Experiment> experiments) {

        try {
            injectFault(ExceptionSpot.ProjectConfigUtils_generateLiveVariableIdToExperimentsMapping_spot1);
            Map<String, List<Experiment>> variableIdToExperiments =
                    new HashMap<String, List<Experiment>>();
            for (Experiment experiment : experiments) {
                injectFault(ExceptionSpot.ProjectConfigUtils_generateLiveVariableIdToExperimentsMapping_spot2);
                if (!experiment.getVariations().isEmpty()) {
                    injectFault(ExceptionSpot.ProjectConfigUtils_generateLiveVariableIdToExperimentsMapping_spot3);
                    // if a live variable is used by an experiment, it will have instances in all variations so we can
                    // short-circuit after getting the live variables for the first variation
                    Variation variation = experiment.getVariations().get(0);
                    if (variation.getLiveVariableUsageInstances() != null) {
                        injectFault(ExceptionSpot.ProjectConfigUtils_generateLiveVariableIdToExperimentsMapping_spot4);
                        for (LiveVariableUsageInstance usageInstance : variation.getLiveVariableUsageInstances()) {
                            List<Experiment> experimentsUsingVariable = variableIdToExperiments.get(usageInstance.getId());
                            if (experimentsUsingVariable == null) {
                                experimentsUsingVariable = new ArrayList<Experiment>();
                            }

                            experimentsUsingVariable.add(experiment);
                            variableIdToExperiments.put(usageInstance.getId(), experimentsUsingVariable);
                        }
                    }
                }
            }

            return variableIdToExperiments;
        } catch (Exception e) {
            FaultInjectionManager.getInstance().throwExceptionIfTreatmentDisabled();
            return null;
        }
    }

    /**
     * Helper method to create a map from variation ID to variable ID to {@link LiveVariableUsageInstance}
     */
    public static Map<String, Map<String, LiveVariableUsageInstance>> generateVariationToLiveVariableUsageInstancesMap(
            List<Experiment> experiments) {

        try {

            injectFault(ExceptionSpot.ProjectConfigUtils_generateVariationToLiveVariableUsageInstancesMap_spot1);
            Map<String, Map<String, LiveVariableUsageInstance>> liveVariableValueMap =
                    new HashMap<String, Map<String, LiveVariableUsageInstance>>();
            for (Experiment experiment : experiments) {
                injectFault(ExceptionSpot.ProjectConfigUtils_generateVariationToLiveVariableUsageInstancesMap_spot2);
                for (Variation variation : experiment.getVariations()) {
                    if (variation.getLiveVariableUsageInstances() != null) {
                        injectFault(ExceptionSpot.ProjectConfigUtils_generateVariationToLiveVariableUsageInstancesMap_spot3);
                        for (LiveVariableUsageInstance usageInstance : variation.getLiveVariableUsageInstances()) {
                            Map<String, LiveVariableUsageInstance> liveVariableIdToValueMap =
                                    liveVariableValueMap.get(variation.getId());
                            if (liveVariableIdToValueMap == null) {
                                liveVariableIdToValueMap = new HashMap<String, LiveVariableUsageInstance>();
                            }

                            liveVariableIdToValueMap.put(usageInstance.getId(), usageInstance);
                            liveVariableValueMap.put(variation.getId(), liveVariableIdToValueMap);
                        }
                    }
                }
            }

            injectFault(ExceptionSpot.ProjectConfigUtils_generateVariationToLiveVariableUsageInstancesMap_spot4);
            return liveVariableValueMap;

        } catch (Exception e) {
            FaultInjectionManager.getInstance().throwExceptionIfTreatmentDisabled();
            return null;
        }
    }
}
