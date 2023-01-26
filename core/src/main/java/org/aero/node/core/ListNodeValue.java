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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class ListNodeValue<N extends ScopedNode<N>, A extends AbstractNode<N, A>> implements NodeValue<N, A> {

    static final Object UNALLOCATED_IDX = new Object() {

    };
    private final A holder;
    private volatile List<A> values;

    ListNodeValue(A holder) {
        this.holder = holder;
        this.values = new ArrayList<>();
    }

    ListNodeValue(A holder, final @Nullable Object startValue) {
        this.holder = holder;
        this.values = new ArrayList<>();
        if (startValue != null) {
            A child = holder.createNode(0);
            child.attached = true;
            child.setRaw(startValue);
            this.values.add(child);
        }
    }

    private List<A> createList() {
        return Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public Object get() {
        final List<Object> ret = new ArrayList<>(this.values.size());
        for (A obj : this.values) {
            ret.add(obj.get()); // unwrap
        }
        return ret;
    }

    public List<N> unwrapped() {
        final List<N> ret = new ArrayList<>(this.values.size());
        for (A element : this.values) {
            ret.add(element.self());
        }
        return Collections.unmodifiableList(ret);
    }

    @Override
    public void set(@Nullable Object value) {
        if (!(value instanceof Collection<?>)) {
            value = Collections.singleton(value);
        }
        final Collection<?> valueAsList = (Collection<?>) value;
        final List<A> newValue = new ArrayList<>(valueAsList.size());

        int count = 0;
        for (@Nullable Object o : valueAsList) {
            if (o == null) {
                continue;
            }

            final A child = this.holder.createNode(count);
            newValue.add(count, child);
            child.attached = true;
            child.setRaw(o);
            ++count;
        }
        synchronized (this) {
            final List<A> oldList = this.values;
            this.values = newValue;
            this.detachChildren(oldList);
        }
    }

    @Override
    public @Nullable A putChild(final Object key, final @Nullable A value) {
        return this.putChildInternal(key, value, false);
    }

    @Override
    public @Nullable A putChildIfAbsent(final Object key, final @Nullable A value) {
        return this.putChildInternal(key, value, true);
    }

    private @Nullable A putChildInternal(final Object index, final @Nullable A value, final boolean onlyIfAbsent) {
        if (index == UNALLOCATED_IDX) {
            if (value != null) { // can't remove an unallocated node
                // Allocate an index for the newly added node
                List<A> values = this.values;
                values.add(value);
                value.key = values.lastIndexOf(value);
            }
            return null;
        } else {
            return this.putChildInternal((int) index, value, onlyIfAbsent);
        }
    }

    private @Nullable A putChildInternal(final int index, final @Nullable A value, final boolean onlyIfAbsent) {
        @Nullable A ret = null;
        final List<A> values = this.values;
        if (value == null) {
            // only remove actually existing values
            if (index >= 0 && index < values.size()) {
                // remove the value
                ret = values.remove(index);
                // update indexes for subsequent elements
                for (int i = index; i < values.size(); ++i) {
                    values.get(i).key = index;
                }
            }
        } else {
            // check if the index is in range
            if (index >= 0 && index < values.size()) {
                if (onlyIfAbsent) {
                    return values.get(index);
                } else {
                    return values.set(index, value);
                }
            } else {
                values.add(index, value);
            }
        }
        return ret;
    }

    @Override
    public @Nullable A child(final @Nullable Object key) {
        if (!(key instanceof Integer value)) {
            return null;
        }
        /*
        final @Nullable Integer value = Scalars.INTEGER.tryDeserialize(key);
        if (value == null || value < 0) {
            return null;
        }
         */

        if (value >= this.values.size()) {
            return null;
        }
        return this.values.get(value);
    }

    @Override
    public Iterable<A> iterateChildren() {
        return Collections.unmodifiableCollection(this.values);
    }

    @Override
    public ListNodeValue<N, A> copy(final A holder) {
        ListNodeValue<N, A> copy = new ListNodeValue<>(holder);
        for (A obj : this.values) {
            copy.values.add(obj.copy(holder)); // recursively copy
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
            final List<A> oldList = this.values;
            this.values = this.createList();
            this.detachChildren(oldList);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ListNodeValue that)) {
            return false;
        }

        return Objects.equals(this.values, that.values);
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    private void detachChildren(List<A> children) {
        for (A node : children) {
            node.attached = false;
            if (Objects.equals(node.parent(), this.holder)) {
                node.clear();
            }
        }
    }
}
