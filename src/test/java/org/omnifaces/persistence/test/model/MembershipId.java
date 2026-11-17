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

import static java.util.Comparator.comparing;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

import jakarta.persistence.Embeddable;

/**
 * The composite ID of a {@link Membership}.
 */
@Embeddable
public class MembershipId implements Comparable<MembershipId>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Comparator<MembershipId> ORDER = comparing(MembershipId::getMemberName).thenComparing(MembershipId::getClubName);

    private String memberName;
    private String clubName;

    public MembershipId() {
        // Required by Jakarta Persistence.
    }

    public MembershipId(String memberName, String clubName) {
        this.memberName = memberName;
        this.clubName = clubName;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getClubName() {
        return clubName;
    }

    @Override
    public int compareTo(MembershipId other) {
        return ORDER.compare(this, other);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof MembershipId other && Objects.equals(memberName, other.memberName) && Objects.equals(clubName, other.clubName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberName, clubName);
    }

    @Override
    public String toString() {
        return memberName + "@" + clubName;
    }

}
