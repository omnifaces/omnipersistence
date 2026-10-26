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

import static jakarta.persistence.EnumType.STRING;

import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

@Entity
public class Phone extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    public enum Type {
        MOBILE,
        HOME,
        WORK;
    }

    private @NotNull @Nullable @Enumerated(STRING) Type type;
    private @NotNull @Nullable String number;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private @NotNull @Nullable Person owner;

    public @Nullable Type getType() {
        return type;
    }

    public void setType(@Nullable Type type) {
        this.type = type;
    }

    public @Nullable String getNumber() {
        return number;
    }

    public void setNumber(@Nullable String number) {
        this.number = number;
    }

    public @Nullable Person getOwner() {
        return owner;
    }

    public void setOwner(Person owner) {
        this.owner = owner;
    }

    @Transient
    public @Nullable String getEmail() {
        return getOwner() == null ? null : getOwner().getEmail();
    }

    @Override
    protected Stream<Function<Phone, @Nullable Object>> identityGetters() {
        return Stream.of(Phone::getType, Phone::getNumber);
    }

}
