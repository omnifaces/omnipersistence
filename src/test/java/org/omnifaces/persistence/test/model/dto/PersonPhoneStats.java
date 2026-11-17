/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.test.model.dto;

import org.omnifaces.persistence.test.model.Person;

public class PersonPhoneStats extends Person {

    private static final long serialVersionUID = 1L;

    private final Long maxPhoneId;
    private final Long sumPhoneId;
    private final Long totalPhoneTypes;
    private final Long sumPhoneIdOrZero;

    public PersonPhoneStats(Long id, Long maxPhoneId, Long sumPhoneId, Long totalPhoneTypes, Long sumPhoneIdOrZero) {
        setId(id);
        this.maxPhoneId = maxPhoneId;
        this.sumPhoneId = sumPhoneId;
        this.totalPhoneTypes = totalPhoneTypes;
        this.sumPhoneIdOrZero = sumPhoneIdOrZero;
    }

    public Long getMaxPhoneId() {
        return maxPhoneId;
    }

    public Long getSumPhoneId() {
        return sumPhoneId;
    }

    public Long getTotalPhoneTypes() {
        return totalPhoneTypes;
    }

    public Long getSumPhoneIdOrZero() {
        return sumPhoneIdOrZero;
    }

}
