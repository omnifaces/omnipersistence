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
package org.omnifaces.persistence.test.model;

import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

@Entity
public class Address extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    private @NotNull @Nullable String street;
    private @NotNull @Nullable String houseNumber;
    private @NotNull @Nullable String postcode;
    private @NotNull @Nullable String city;
    private @NotNull @Nullable String country;

    public @Nullable String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public @Nullable String getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    public @Nullable String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public @Nullable String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public @Nullable String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

}
