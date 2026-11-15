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

import static java.time.ZoneOffset.UTC;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;

import jakarta.persistence.Entity;

@Entity
public class Appointment extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    private LocalTime startLocalTime;
    private LocalDateTime startLocalDateTime;
    private OffsetTime startOffsetTime;
    private OffsetDateTime startOffsetDateTime;

    /**
     * Returns a new appointment starting at the given date and time, in UTC for the offset based fields.
     */
    public static Appointment startingAt(LocalDateTime start) {
        var appointment = new Appointment();
        appointment.startLocalTime = start.toLocalTime();
        appointment.startLocalDateTime = start;
        appointment.startOffsetTime = start.toLocalTime().atOffset(UTC);
        appointment.startOffsetDateTime = start.atOffset(UTC);
        return appointment;
    }

    public LocalTime getStartLocalTime() {
        return startLocalTime;
    }

    public LocalDateTime getStartLocalDateTime() {
        return startLocalDateTime;
    }

    public OffsetTime getStartOffsetTime() {
        return startOffsetTime;
    }

    public OffsetDateTime getStartOffsetDateTime() {
        return startOffsetDateTime;
    }

}
