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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class MapNodeValue<N extends ScopedNode<N>, A extends AbstractNode<N, A>> implements NodeValue<N, A> {

    private final A holder;
    private volatile Map<Object, A> values;

    MapNodeValue(A holder) {
        this.holder = holder;
        this.values = this.createMap();
    }

    private Map<Object, A> createMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    public Object get() {
        final Map<Object, Object> ret = new LinkedHashMap<>();
        for (final Map.Entry<Object, A> ent : this.values.entrySet()) {
            ret.put(ent.getKey(), ent.getValue().get()); // unwrap key from the backing node
        }
        return ret;
    }

    public Map<Object, N> unwrapped() {
        final Map<Object, N> ret = new LinkedHashMap<>();
        for (final Map.Entry<Object, A> ent : this.values.entrySet()) {
            ret.put(ent.getKey(), ent.getValue().self());
        }
        return Collections.unmodifiableMap(ret);
    }

    @Override
    public void set(@Nullable Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Map configuration values can only be set to values of type Map");
        }
        final Map<Object, A> newValue = createMap();
        for (final Map.Entry<?, ?> ent : ((Map<?, ?>) value).entrySet()) {
            if (ent.getValue() == null) {
                continue;
            }
            final A child = this.holder.createNode(ent.getKey());
            newValue.put(ent.getKey(), child);
            child.attached = true;
            child.setRaw(ent.getValue());
        }
        synchronized (this) {
            final Map<Object, A> oldMap = this.values;
            this.values = newValue;
            this.detachChildren(oldMap);
        }
    }

    @Override
    public @Nullable A putChild(final Object key, final @Nullable A value) {
        if (value == null) {
            return this.values.remove(key);
        } else {
            return this.values.put(key, value);
        }
    }

    @Override
    public @Nullable A putChildIfAbsent(final Object key, final @Nullable A value) {
        if (value == null) {
            return this.values.remove(key);
        } else {
            return this.values.putIfAbsent(key, value);
        }
    }

    @Override
    public @Nullable A child(final @Nullable Object key) {
        return this.values.get(key);
    }

    @Override
    public Iterable<A> iterateChildren() {
        return this.values.values();
    }

    @Override
    public MapNodeValue<N, A> copy(A holder) {
        MapNodeValue<N, A> copy = new MapNodeValue<>(holder);
        for (Map.Entry<Object, A> ent : this.values.entrySet()) {
            copy.values.put(ent.getKey(), ent.getValue().copy(holder)); // recursively copy
        }
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    @Override
    public void clear() {
        synchronized (this) {
            final Map<Object, A> oldMap = this.values;
            this.values = this.createMap();
            this.detachChildren(oldMap);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof MapNodeValue that)) {
            return false;
        }

        return Objects.equals(this.values, that.values);
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    private void detachChildren(final Map<Object, A> map) {
        for (A value : map.values()) {
            value.attached = false;
            if (Objects.equals(value.parent(), this.holder)) {
                value.clear();
            }
        }
    }
}
