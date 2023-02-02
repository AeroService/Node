/*
 * Copyright 2020-2023 AeroService
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

package org.aero.node.core;import org.jetbrains.annotations.Nullable;

final class BasicNodeImpl extends AbstractNode<BasicNode, BasicNodeImpl> implements BasicNode {

    BasicNodeImpl(@Nullable Object key, @Nullable BasicNodeImpl parent, boolean bool) {
        super(key, parent, bool);
    }

    BasicNodeImpl(@Nullable BasicNodeImpl parent, BasicNodeImpl copyOf) {
        super(parent, copyOf);
    }

    @Override
    protected BasicNodeImpl createNode(Object path) {
        return new BasicNodeImpl(path, this, false);
    }

    @Override
    protected BasicNodeImpl copy(@Nullable BasicNodeImpl parent) {
        return new BasicNodeImpl(parent, this);
    }

    @Override
    protected BasicNodeImpl implSelf() {
        return this;
    }

    @Override
    public BasicNode self() {
        return this;
    }
}
