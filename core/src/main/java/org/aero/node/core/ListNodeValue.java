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

    @SuppressWarnings({"rawtypes"})
    static final AtomicReferenceFieldUpdater<ListNodeValue, List> VALUES_HANDLE =
        AtomicReferenceFieldUpdater.newUpdater(ListNodeValue.class, List.class, "values");

    static final Object UNALLOCATED_IDX = new Object() {
    };
    private final A holder;
    volatile List<A> values = new ArrayList<>();
    ListNodeValue(A holder) {
        this.holder = holder;
    }

    ListNodeValue(A holder, final @Nullable Object startValue) {
        this.holder = holder;
        if (startValue != null) {
            A child = holder.createNode(0);
            child.attached = true;
            child.setRaw(startValue);
            this.values.add(child);
        }
    }

    static boolean likelyListKey(@Nullable Object key) {
        return key instanceof Integer || key == UNALLOCATED_IDX;
    }

    @Override
    public Object get() {
        synchronized (this.values) {
            final List<Object> ret = new ArrayList<>(this.values.size());
            for (A obj : this.values) {
                ret.add(obj.get()); // unwrap
            }
            return ret;
        }
    }

    public List<N> unwrapped() {
        final List<A> orig = this.values;
        synchronized (orig) {
            final List<N> ret = new ArrayList<>(orig.size());
            for (A element : orig) {
                ret.add(element.self());
            }
            return Collections.unmodifiableList(ret);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
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
        detachNodes(VALUES_HANDLE.getAndSet(this, newValue));
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
                List<A> values;
                do {
                    // Allocate an index for the newly added node
                    values = this.values;
                    values.add(value);
                    value.key = values.lastIndexOf(value);
                } while (!VALUES_HANDLE.compareAndSet(this, values, values));
            }
            return null;
        } else {
            return putChildInternal((int) index, value, onlyIfAbsent);
        }
    }

    private @Nullable A putChildInternal(final int index, final @Nullable A value, final boolean onlyIfAbsent) {
        @Nullable A ret = null;
        List<A> values;
        do {
            values = this.values;
            synchronized (values) {
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
                            ret = values.set(index, value);
                        }
                    } else {
                        values.add(index, value);
                    }
                }
            }
        } while (!VALUES_HANDLE.compareAndSet(this, values, values));
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

        synchronized (this.values) {
            if (value >= this.values.size()) {
                return null;
            }
            return this.values.get(value);
        }
    }

    @Override
    public Iterable<A> iterateChildren() {
        synchronized (this.values) {
            return Collections.unmodifiableCollection(this.values);
        }
    }

    @Override
    public ListNodeValue<N, A> copy(final A holder) {
        ListNodeValue<N, A> copy = new ListNodeValue<>(holder);
        List<A> copyValues;

        synchronized (this.values) {
            copyValues = new ArrayList<>(this.values.size());
            for (A obj : this.values) {
                copyValues.add(obj.copy(holder)); // recursively copy
            }
        }

        copy.values = copyValues;
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    private void detachNodes(List<A> children) {
        synchronized (children) {
            for (A node : children) {
                node.attached = false;
                if (Objects.equals(node.parent(), this.holder)) {
                    node.clear();
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void clear() {
        final List<A> oldValues = VALUES_HANDLE.getAndSet(this, new ArrayList<>());
        detachNodes(oldValues);
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
}
