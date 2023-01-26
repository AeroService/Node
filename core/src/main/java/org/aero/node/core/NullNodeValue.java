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

import java.util.Collections;

final class NullNodeValue<N extends ScopedNode<N>, A extends AbstractNode<N, A>> implements NodeValue<N, A> {

    @SuppressWarnings("rawtypes")
    private static final NullNodeValue INSTANCE = new NullNodeValue();

    private NullNodeValue() {

    }

    @SuppressWarnings("unchecked")
    public static <N extends ScopedNode<N>, A extends AbstractNode<N, A>> NullNodeValue<N, A> instance() {
        return NullNodeValue.INSTANCE;
    }

    @Override
    public @Nullable Object get() {
        return null;
    }

    @Override
    public void set(final @Nullable Object value) {
        throw new UnsupportedOperationException("Value should be changed from null type before setting value");
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
    public NullNodeValue<N, A> copy(final A holder) {
        return instance();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public void clear() {
        // empty
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof NullNodeValue;
    }

    @Override
    public int hashCode() {
        return 1009;
    }
}
