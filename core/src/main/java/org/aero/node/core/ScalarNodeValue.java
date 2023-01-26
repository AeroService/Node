/*
 * Copyright 2020-2022 NatroxMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.aero.node.core;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

final class ScalarNodeValue<N extends ScopedNode<N>, A extends AbstractNode<N, A>> implements NodeValue<N, A> {

    private final A holder;
    private volatile @Nullable Object value;

    ScalarNodeValue(A holder) {
        this.holder = holder;
    }

    @Override
    public @Nullable Object get() {
        return this.value;
    }

    @Override
    public void set(final @Nullable Object value) {
        // if (value != null && !this.holder.options().acceptsType(value.getClass())) {
        //     throw new IllegalArgumentException("Configuration does not accept objects of type " + value.getClass());
        // }
        this.value = value;
    }

    @Override
    public @Nullable A putChild(final Object key, final @Nullable A value) {
        return null;
    }

    @Override
    public @Nullable A putChildIfAbsent(final Object key, final @Nullable A value) {
        return null;
    }

    @Override
    public @Nullable A child(final @Nullable Object key) {
        return null;
    }

    @Override
    public Iterable<A> iterateChildren() {
        return Collections.emptySet();
    }

    @Override
    public ScalarNodeValue<N, A> copy(final A holder) {
        ScalarNodeValue<N, A> copy = new ScalarNodeValue<>(holder);
        copy.value = this.value;
        return copy;
    }

    @Override
    @SuppressWarnings("checkstyle:UnnecessaryParentheses")
    public boolean isEmpty() {
        final @Nullable Object value = this.value;
        return (value instanceof String && ((String) value).isEmpty())
            || (value instanceof Collection<?> && ((Collection<?>) value).isEmpty());
    }

    @Override
    public void clear() {
        this.value = null;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ScalarNodeValue that)) {
            return false;
        }

        return Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return 7 + Objects.hashCode(this.value);
    }
}
