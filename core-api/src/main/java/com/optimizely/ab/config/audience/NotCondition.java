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
package com.optimizely.ab.config.audience;

import com.optimizely.ab.faultinjection.ExceptionSpot;
import com.optimizely.ab.faultinjection.FaultInjectionManager;

import javax.annotation.concurrent.Immutable;
import javax.annotation.Nonnull;

import java.util.Map;

/**
 * Represents a 'Not' conditions condition operation.
 */
@Immutable
public class NotCondition implements Condition {

    private Condition condition;

    private static void injectFault(ExceptionSpot spot) {
        FaultInjectionManager.getInstance().injectFault(spot);
    }
    private static void throwInjectedExceptionIfTreatmentDisabled() { FaultInjectionManager.getInstance().throwExceptionIfTreatmentDisabled(); }

    public NotCondition(@Nonnull Condition condition) {
        try {
            injectFault(ExceptionSpot.NotCondition_constructor_spot1);
            this.condition = condition;
        } catch (Exception e) {
            throwInjectedExceptionIfTreatmentDisabled();
        }
    }

    public Condition getCondition() {
        return condition;
    }

    public boolean evaluate(Map<String, String> attributes) {
        try {
            injectFault(ExceptionSpot.NotCondition_evaluate_spot1);
            return !condition.evaluate(attributes);
        } catch (Exception e) {
            throwInjectedExceptionIfTreatmentDisabled();
            return false;
        }
    }

    @Override
    public String toString() {
        try {
            injectFault(ExceptionSpot.NotCondition_toString_spot1);
            StringBuilder s = new StringBuilder();

            s.append("[not, ");
            s.append(condition);
            s.append("]");
            injectFault(ExceptionSpot.NotCondition_toString_spot2);
            return s.toString();
        } catch (Exception e) {
            throwInjectedExceptionIfTreatmentDisabled();
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        try {
            injectFault(ExceptionSpot.NotCondition_equals_spot1);

            if (!(other instanceof NotCondition))
                return false;

            NotCondition otherNotCondition = (NotCondition) other;

            injectFault(ExceptionSpot.NotCondition_equals_spot2);
            return condition.equals(otherNotCondition.getCondition());
        } catch (Exception e) {
            throwInjectedExceptionIfTreatmentDisabled();
            return false;
        }
    }

    @Override
    public int hashCode() {
        try {
            injectFault(ExceptionSpot.NotCondition_hashCode_spot1);
            return condition.hashCode();
        } catch (Exception e) {
            throwInjectedExceptionIfTreatmentDisabled();
            return 0;
        }
    }
}
