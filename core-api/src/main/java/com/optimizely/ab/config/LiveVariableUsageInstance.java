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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.optimizely.ab.faultinjection.ExceptionSpot;
import com.optimizely.ab.faultinjection.FaultInjectionManager;

/**
 * Represents the value of a live variable for a variation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiveVariableUsageInstance implements IdMapped {

    private final String id;
    private final String value;

    private static void injectFault(ExceptionSpot spot) {
        FaultInjectionManager.getInstance().injectFault(spot);
    }

    @JsonCreator
    public LiveVariableUsageInstance(@JsonProperty("id") String id,
                                     @JsonProperty("value") String value) {
        this.id = id;
        this.value = value;
    }

    public String getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        try {
            injectFault(ExceptionSpot.LiveVariableUsageInstance_equals_spot1);
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LiveVariableUsageInstance that = (LiveVariableUsageInstance) o;
            injectFault(ExceptionSpot.LiveVariableUsageInstance_equals_spot2);
            return id.equals(that.id) && value.equals(that.value);
        } catch (Exception e) {
            FaultInjectionManager.getInstance().throwExceptionIfTreatmentDisabled();
            return false;
        }
    }

    @Override
    public int hashCode() {
        try {
            injectFault(ExceptionSpot.LiveVariableUsageInstance_hashCode_spot1);
            int result = id.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        } catch (Exception e) {
            FaultInjectionManager.getInstance().throwExceptionIfTreatmentDisabled();
            return 0;
        }
    }
}
