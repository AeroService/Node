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

interface NodeValue<N extends ScopedNode<N>, A extends AbstractNode<N, A>> {

    @Nullable Object get();

    void set(@Nullable Object value);

    @Nullable A putChild(Object key, @Nullable A value);

    @Nullable A putChildIfAbsent(Object key, @Nullable A value);

    @Nullable A child(@Nullable Object key);

    Iterable<A> iterateChildren();

    NodeValue<N, A> copy(A holder);

    boolean isEmpty();

    void clear();

}
