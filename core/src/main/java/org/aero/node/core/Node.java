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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ApiStatus.Experimental
public interface Node {

    @Nullable Object key();

    @Nullable Node parent();

    @NotNull Node node(@NotNull Iterable<?> path);

    default @NotNull Node node(Object @NotNull ... path) {
        return this.node(Arrays.asList(path));
    }

    boolean hasChild(@NotNull Iterable<?> path);

    default boolean hasChild(@NotNull Object... path) {
        return this.hasChild(Arrays.asList(path));
    }

    @NotNull Node appendChild();

    boolean removeChild(@NotNull Object key);

    boolean isVirtual();

    boolean isNull();

    boolean isList();

    boolean isMap();

    boolean isEmpty();

    @Nullable Object get();

    <T> @Nullable T getAs(@NotNull Class<T> type);

    <T> @Nullable T getAsOrDefault(@NotNull Class<T> type, T def);

    <T> @Nullable T getAsOrDefault(@NotNull Class<T> type, @NotNull Supplier<T> defSupplier);

    @NotNull Node set(Object value);

    @NotNull Node setRaw(Object value);

    Node from(@NotNull Node other);

    Node mergeFrom(@NotNull Node other);

    @Nullable Object rawScalar();

    List<? extends Node> childrenList();

    Map<Object, ? extends Node> childrenMap();

    @NotNull Node copy();

}
