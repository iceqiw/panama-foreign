/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.CSupport;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.ForeignLinker;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

import static jdk.incubator.foreign.CSupport.C_DOUBLE;
import static jdk.incubator.foreign.CSupport.C_INT;
import static jdk.incubator.foreign.CSupport.C_LONGLONG;
import static jdk.incubator.foreign.CSupport.C_POINTER;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
public class CallOverhead {

    static final ForeignLinker abi = CSupport.getSystemLinker();
    static final MethodHandle func;
    static final MethodHandle identity;
    static final MethodHandle identity_struct;
    static final MethodHandle identity_memory_address;
    static final MethodHandle args5;
    static final MethodHandle args10;

    static final MemoryLayout POINT_LAYOUT = MemoryLayout.ofStruct(
        C_LONGLONG, C_LONGLONG
    );

    static final MemorySegment point = MemorySegment.allocateNative(POINT_LAYOUT);

    static {
        System.loadLibrary("CallOverheadJNI");

        try {
            LibraryLookup ll = LibraryLookup.ofLibrary("CallOverhead");
            func = abi.downcallHandle(ll.lookup("func"),
                    MethodType.methodType(void.class),
                    FunctionDescriptor.ofVoid());
            identity = abi.downcallHandle(ll.lookup("identity"),
                    MethodType.methodType(int.class, int.class),
                    FunctionDescriptor.of(C_INT, C_INT));
            identity_struct = abi.downcallHandle(ll.lookup("identity_struct"),
                    MethodType.methodType(MemorySegment.class, MemorySegment.class),
                    FunctionDescriptor.of(POINT_LAYOUT, POINT_LAYOUT));
            identity_memory_address = abi.downcallHandle(ll.lookup("identity_memory_address"),
                    MethodType.methodType(MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_POINTER, C_POINTER));
            args5 = abi.downcallHandle(ll.lookup("args5"), // just reuse identity
                    MethodType.methodType(void.class, long.class, double.class, long.class, double.class, long.class),
                    FunctionDescriptor.ofVoid(C_LONGLONG, C_DOUBLE, C_LONGLONG, C_DOUBLE, C_LONGLONG));
            args10 = abi.downcallHandle(ll.lookup("args10"),
                    MethodType.methodType(void.class, long.class, double.class, long.class, double.class, long.class,
                                                      double.class, long.class, double.class, long.class, double.class),
                    FunctionDescriptor.ofVoid(C_LONGLONG, C_DOUBLE, C_LONGLONG, C_DOUBLE, C_LONGLONG,
                                              C_DOUBLE, C_LONGLONG, C_DOUBLE, C_LONGLONG, C_DOUBLE));
        } catch (NoSuchMethodException e) {
            throw new BootstrapMethodError(e);
        }
    }

    static native void blank();
    static native int identity(int x);

    @Benchmark
    public void jni_blank() throws Throwable {
        blank();
    }

    @Benchmark
    public void panama_blank() throws Throwable {
        func.invokeExact();
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djdk.internal.foreign.ProgrammableInvoker.NO_SPEC=true")
    public void panama_blank_NO_SPEC() throws Throwable {
        func.invokeExact();
    }

    @Benchmark
    public int jni_identity() throws Throwable {
        return identity(10);
    }

    @Benchmark
    public int panama_identity() throws Throwable {
        return (int) identity.invokeExact(10);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djdk.internal.foreign.ProgrammableInvoker.NO_SPEC=true")
    public int panama_identity_NO_SPEC() throws Throwable {
        return (int) identity.invokeExact(10);
    }

    @Benchmark
    public MemorySegment panama_identity_struct() throws Throwable {
        return (MemorySegment) identity_struct.invokeExact(point);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djdk.internal.foreign.ProgrammableInvoker.NO_SPEC=true")
    public MemorySegment panama_identity_struct_NO_SPEC() throws Throwable {
        return (MemorySegment) identity_struct.invokeExact(point);
    }

    @Benchmark
    public MemoryAddress panama_identity_memory_address() throws Throwable {
        return (MemoryAddress) identity_memory_address.invokeExact(MemoryAddress.NULL);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djdk.internal.foreign.ProgrammableInvoker.NO_SPEC=true")
    public MemoryAddress panama_identity_memory_address_NO_SPEC() throws Throwable {
        return (MemoryAddress) identity_memory_address.invokeExact(MemoryAddress.NULL);
    }

    @Benchmark
    public void panama_args5() throws Throwable {
        args5.invokeExact(10L, 11D, 12L, 13D, 14L);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djdk.internal.foreign.ProgrammableInvoker.NO_SPEC=true")
    public void panama_args5_NO_SPEC() throws Throwable {
        args5.invokeExact(10L, 11D, 12L, 13D, 14L);
    }

    @Benchmark
    public void panama_args10() throws Throwable {
        args10.invokeExact(10L, 11D, 12L, 13D, 14L,
                           15D, 16L, 17D, 18L, 19D);
    }

    @Benchmark
    @Fork(jvmArgsAppend = "-Djdk.internal.foreign.ProgrammableInvoker.NO_SPEC=true")
    public void panama_args10_NO_SPEC() throws Throwable {
        args10.invokeExact(10L, 11D, 12L, 13D, 14L,
                           15D, 16L, 17D, 18L, 19D);
    }
}
