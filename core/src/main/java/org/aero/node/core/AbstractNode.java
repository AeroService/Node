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

import org.aero.common.core.validate.Check;
import org.aero.conversion.core.ConversionBus;
import org.aero.conversion.core.ObjectMappingConversionBus;
import org.aero.conversion.core.exception.ConversionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public abstract class AbstractNode<N extends ScopedNode<N>, A extends AbstractNode<N, A>> implements ScopedNode<N> {

    private final ConversionBus conversionService = ObjectMappingConversionBus.createDefault();

    volatile boolean attached;
    volatile @Nullable Object key;
    volatile NodeValue<N, A> value;

    private @Nullable A parent;

    //TODO: Replace bool with something else
    protected AbstractNode(@Nullable Object key, @Nullable A parent, boolean bool) {
        Check.argCondition((key == null) != (parent == null),
            "The key and the parent must share the same nullability status");
        this.key = key;
        this.parent = parent;
        this.value = NullNodeValue.instance();

        if (parent == null) {
            this.attached = true;
        }
    }

    protected AbstractNode(@Nullable A parent, A copyOf) {
        this.attached = true; // copies are always attached
        this.key = copyOf.key;
        this.parent = parent;
        this.value = copyOf.value.copy(this.implSelf());
    }

    @Override
    public final @Nullable Object key() {
        return this.key;
    }

    @Override
    public final @Nullable N parent() {
        A parent = this.parent;
        return parent == null ? null : parent.self();
    }

    @Override
    public final @NotNull N node(@NotNull Iterable<?> path) {
        A pointer = this.implSelf();
        for (final Object element : path) {
            Check.notNull(element, "element in path");
            pointer = pointer.child(element);
        }
        return pointer.self();
    }

    @Override
    public final boolean hasChild(@NotNull Iterable<?> path) {
        A pointer = this.implSelf();
        for (Object element : path) {
            Check.notNull(element, "element in path");
            A child = pointer.value.child(element);
            if (child == null) {
                return false;
            }
            pointer = child;
        }
        return true;
    }

    @Override
    public final @NotNull N appendChild() {
        // the appended node can have a key of -1
        // the "real" key will be determined when the node is inserted into a list config value
        return this.child(ListNodeValue.UNALLOCATED_IDX).self();
    }

    @Override
    public final boolean removeChild(final @NotNull Object key) {
        return this.detachIfNonNull(this.value.putChild(key, null)) != null;
    }

    @Override
    public final boolean isVirtual() {
        return !this.attached;
    }

    @Override
    public boolean isNull() {
        return this.value instanceof NullNodeValue;
    }

    @Override
    public final boolean isList() {
        return this.value instanceof ListNodeValue;
    }

    @Override
    public final boolean isMap() {
        return this.value instanceof MapNodeValue;
    }

    @Override
    public boolean isEmpty() {
        return this.value.isEmpty();
    }

    @Override
    public final @Nullable Object get() {
        return this.value.get();
    }

    @Override
    public <T> @Nullable T getAs(@NotNull Class<T> type) {
        return this.get0(type);
    }

    @Override
    public <T> @Nullable T getAsOrDefault(@NotNull Class<T> type, T def) {
        T value = this.get0(type);
        return value == null ? this.storeDefault(def) : value;
    }

    @Override
    public <T> @Nullable T getAsOrDefault(@NotNull Class<T> type, @NotNull Supplier<T> defSupplier) {
        T value = this.get0(type);
        return value == null ? this.storeDefault(defSupplier.get()) : value;
    }

    private <V> V storeDefault(V defValue) {
        this.set(defValue);
        return defValue;
    }

    @SuppressWarnings("ConstantConditions")
    private <T> @Nullable T get0(@NotNull Class<T> type) {
        Check.notNull(type, "type");
        //if (isMissingTypeParameters(type)) {
        //    throw new SerializationException(this, type, "Raw types are not supported");
        //}

        if (this.value instanceof NullNodeValue) {
            return null;
        }

        Object value = this.get();
        if (type.isInstance(value)) {
            return type.cast(value);
        } else {
            try {
                return this.conversionService.convert(value, type);
            } catch (ConversionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public final @NotNull N set(final @Nullable Object newValue) {
        // if the value to be set is a configuration node already, unwrap and store the raw data
        if (newValue instanceof Node) {
            this.from((Node) newValue);
            return this.self();
        }

        // if the new value is null, handle detaching from this nodes parent
        if (newValue == null) {
            final @Nullable Object key = this.key;
            if (this.parent == null || key == null) {
                this.clear();
            } else {
                this.parent.removeChild(key);
            }
            return this.self();
        } else if (newValue instanceof Collection || newValue instanceof Map) {
            this.insertNewValue(newValue, false);
            return this.self();
        } else {
            Class<?> type = newValue.getClass();
            if (!type.isInstance(newValue)) {
                throw new IllegalStateException();
            }

            try {
                final Object result = this.conversionService.convertToObject(newValue);

                this.setRaw(result);
            } catch (ConversionException e) {
                throw new RuntimeException(e);
            }

            return this.self();
        }
    }

    @Override
    public final @NotNull N setRaw(@Nullable Object newValue) {
        // if the new value is null, handle detaching from this nodes parent
        if (newValue == null) {
            final Object key = this.key;
            if (this.parent == null || key == null) {
                this.clear();
            } else {
                this.parent.removeChild(key);
            }
        } else {
            this.insertNewValue(newValue, false);
        }

        return this.self();
    }

    @Override
    public N from(@NotNull Node that) {
        if (that == this) { // this would be a no-op whoop
            return this.self();
        }

        if (that.isList()) {
            // handle list
            this.attachIfNecessary();
            ListNodeValue<N, A> newList = new ListNodeValue<>(this.implSelf());
            synchronized (that) {
                final List<? extends Node> children = that.childrenList();
                for (int i = 0; i < children.size(); i++) {
                    final A node = this.createNode(i);
                    node.attached = true;
                    node.from(children.get(i));
                    newList.putChild(i, node);
                }
            }
            this.value = newList;
        } else if (that.isMap()) {
            // handle map
            this.attachIfNecessary();
            final MapNodeValue<N, A> newMap = new MapNodeValue<>(this.implSelf());
            synchronized (that) {
                for (final Map.Entry<Object, ? extends Node> entry : that.childrenMap().entrySet()) {
                    final A node = this.createNode(entry.getKey());
                    node.attached = true;
                    node.from(entry.getValue());
                    newMap.putChild(entry.getKey(), node);
                }
            }
            this.value = newMap;
        } else {
            // handle scalar/null
            this.setRaw(that.get());
        }

        return this.self();
    }

    @Override
    public N mergeFrom(@NotNull Node other) {
        // If we are empty, then just directly set our value from the source
        if ((this.isVirtual() || this.isEmpty()) && !other.isVirtual()) {
            return this.from(other);
        }

        if (other.isMap()) {
            NodeValue<N, A> oldValue;
            NodeValue<N, A> newValue;
            synchronized (this) {
                oldValue = newValue = this.value;

                // ensure the current type is applicable.
                if (!(oldValue instanceof MapNodeValue)) {
                    if (oldValue instanceof NullNodeValue) {
                        newValue = new MapNodeValue<>(this.implSelf());
                    } else {
                        return this.self();
                    }
                }

                // merge values from 'other'
                for (final Map.Entry<Object, ? extends Node> ent : other.childrenMap().entrySet()) {
                    A currentChild = newValue.child(ent.getKey());
                    // Never allow null values to overwrite non-null values
                    if (currentChild != null && currentChild.get() != null && ent.getValue().get() == null) {
                        continue;
                    }

                    // create a new child node for the value
                    final A newChild = this.createNode(ent.getKey());
                    newChild.attached = true;
                    newChild.from(ent.getValue());
                    // replace the existing value, if absent
                    final @Nullable A existing = newValue.putChildIfAbsent(ent.getKey(), newChild);
                    // if an existing value was present, attempt to merge the new value into it
                    if (existing != null) {
                        existing.mergeFrom(newChild);
                    }
                }
                this.value = newValue;
            }
        } else if (other.isList()) {
            if (this.isVirtual()) {
                this.from(other);
            }
        } else if (other.rawScalar() != null) {
            // otherwise, replace the value of this node, only if currently null
            this.insertNewValue(other.rawScalar(), true);
        }
        return this.self();
    }

    private void insertNewValue(final Object newValue, final boolean onlyIfNull) {
        Check.argCondition(newValue instanceof Node, "Cannot set a node as the raw value of another node");
        this.attachIfNecessary();

        synchronized (this) {
            NodeValue<N, A> oldValue;
            NodeValue<N, A> value;
            oldValue = value = this.value;

            if (onlyIfNull && !(oldValue instanceof NullNodeValue)) {
                return;
            }

            // init new config value backing for the new value type if necessary
            if (newValue instanceof Collection) {
                if (!(value instanceof ListNodeValue)) {
                    value = new ListNodeValue<>(this.implSelf());
                }
            } else if (newValue instanceof Map) {
                if (!(value instanceof MapNodeValue)) {
                    value = new MapNodeValue<>(this.implSelf());
                }
            } else if (!(value instanceof ScalarNodeValue)) {
                value = new ScalarNodeValue<>(this.implSelf());
            }

            // insert the data into the config value
            value.set(newValue);
            this.value = value;
        }
    }

    @Override
    public final @Nullable Object rawScalar() {
        NodeValue<N, A> value = this.value;
        if (value instanceof ScalarNodeValue<?, ?>) {
            return value.get();
        } else {
            return null;
        }
    }

    @Override
    public final List<N> childrenList() {
        NodeValue<N, A> value = this.value;
        return value instanceof ListNodeValue<N, A> ? ((ListNodeValue<N, A>) value).unwrapped()
            : Collections.emptyList();
    }

    @Override
    public final Map<Object, N> childrenMap() {
        NodeValue<N, A> value = this.value;
        return value instanceof MapNodeValue<N, A> mapValue ? mapValue.unwrapped() : Collections.emptyMap();
    }

    protected final A child(final Object key) {
        A child = this.value.child(key);

        // child doesn't currently exist
        if (child == null) {
            // just create a new virtual (detached) node
            child = this.createNode(key);
        }

        return child;
    }

    @Override
    public final @NotNull N copy() {
        return this.copy(null).self();
    }

    protected final @Nullable A parentEnsureAttached() {
        @Nullable A parent = this.parent;
        if (parent != null && parent.isVirtual()) {
            A temp = parent.parentEnsureAttached();
            parent = temp != null ? temp.attachChildIfAbsent(parent) : null;
        }
        return this.parent = parent;
    }

    protected final void attachIfNecessary() {
        if (!this.attached) {
            final @Nullable A parent = this.parentEnsureAttached();
            if (parent != null) {
                parent.attachChild(this.implSelf());
            }
        }
    }

    protected final A attachChildIfAbsent(final A child) {
        return this.attachChild(child, true);
    }

    protected final void attachChild(final A child) {
        this.attachChild(child, false);
    }

    private A attachChild(final A child, final boolean onlyIfAbsent) {
        // ensure this node is attached
        if (this.isVirtual()) {
            throw new IllegalStateException("This parent is not currently attached. This is an internal state violation.");
        }

        // ensure the child actually is a child
        if (!Objects.equals(child.parentEnsureAttached(), this)) {
            throw new IllegalStateException("Child " + child + " path is not a direct parent of me (), cannot attach");
        }

        // update the value
        NodeValue<N, A> oldValue;
        NodeValue<N, A> newValue;
        synchronized (this) {
            newValue = oldValue = this.value;

            if (oldValue instanceof MapNodeValue) {
                if (child.key == ListNodeValue.UNALLOCATED_IDX) {
                    newValue = new ListNodeValue<>(this.implSelf());
                }
            } else {
                // if the existing value isn't a map, we need to update it's type
                if (child.key instanceof Integer || child.key == ListNodeValue.UNALLOCATED_IDX) {
                    // if child.key is an integer, we can infer that the type of this node should be a list
                    if (oldValue instanceof NullNodeValue) {
                        // if the oldValue was null, we can just replace it with an empty list
                        newValue = new ListNodeValue<>(this.implSelf());
                    } else if (!(oldValue instanceof ListNodeValue)) {
                        // if the oldValue contained a value, we add it as the first element of the
                        // new list
                        newValue = new ListNodeValue<>(this.implSelf(), oldValue.get());
                    }
                } else {
                    // if child.key isn't an integer, assume map
                    newValue = new MapNodeValue<>(this.implSelf());
                }
            }

            /// now the value has been updated to an appropriate type, we can insert the value
            final @Nullable Object childKey = child.key;
            if (childKey == null) {
                throw new IllegalArgumentException("Cannot attach a child with null key");
            }

            if (onlyIfAbsent) {
                final @Nullable A oldChild = newValue.putChildIfAbsent(childKey, child);
                if (oldChild != null) {
                    return oldChild;
                }
            } else {
                this.detachIfNonNull(newValue.putChild(childKey, child));
            }
            this.value = newValue;
        }

        if (newValue != oldValue) {
            oldValue.clear();
        }
        child.attached = true;
        return child;
    }

    private @Nullable A detachIfNonNull(A node) {
        if (node != null) {
            node.attached = false;
            node.clear();
        }
        return node;
    }

    protected final void clear() {
        synchronized (this) {
            NodeValue<N, A> oldValue = this.value;
            this.value = NullNodeValue.instance();
            oldValue.clear();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AbstractNode<?, ?> that)) {
            return false;
        }

        return Objects.equals(this.key, that.key) && Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.key) ^ Objects.hashCode(this.value);
    }

    protected abstract A createNode(Object path);

    protected abstract A copy(@Nullable A parent);

    protected abstract A implSelf();

}
