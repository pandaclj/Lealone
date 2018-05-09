/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.storage.type;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;

import org.lealone.common.util.DataUtils;
import org.lealone.db.DataBuffer;
import org.lealone.db.value.Value;
import org.lealone.db.value.ValueString;

public class ObjectArrayType extends StorageDataTypeBase {

    private final ObjectDataType elementType = new ObjectDataType();

    @Override
    public int getType() {
        return TYPE_ARRAY;
    }

    @Override
    public int compare(Object aObj, Object bObj) {
        if (aObj == bObj) {
            return 0;
        }
        Class<?> type = aObj.getClass().getComponentType();
        Class<?> bType = bObj.getClass().getComponentType();
        if (type != bType) {
            Integer classA = ObjectDataType.getCommonClassId(type);
            Integer classB = ObjectDataType.getCommonClassId(bType);
            if (classA != null) {
                if (classB != null) {
                    return classA.compareTo(classB);
                }
                return -1;
            } else if (classB != null) {
                return 1;
            }
            return type.getName().compareTo(bType.getName());
        }
        int aLen = Array.getLength(aObj);
        int bLen = Array.getLength(bObj);
        int len = Math.min(aLen, bLen);
        if (type.isPrimitive()) {
            if (type == byte.class) {
                byte[] a = (byte[]) aObj;
                byte[] b = (byte[]) bObj;
                return ObjectDataType.compareNotNull(a, b);
            }
            for (int i = 0; i < len; i++) {
                int x;
                if (type == boolean.class) {
                    x = Integer.signum((((boolean[]) aObj)[i] ? 1 : 0) - (((boolean[]) bObj)[i] ? 1 : 0));
                } else if (type == char.class) {
                    x = Integer.signum((((char[]) aObj)[i]) - (((char[]) bObj)[i]));
                } else if (type == short.class) {
                    x = Integer.signum((((short[]) aObj)[i]) - (((short[]) bObj)[i]));
                } else if (type == int.class) {
                    int a = ((int[]) aObj)[i];
                    int b = ((int[]) bObj)[i];
                    x = a == b ? 0 : a < b ? -1 : 1;
                } else if (type == float.class) {
                    x = Float.compare(((float[]) aObj)[i], ((float[]) bObj)[i]);
                } else if (type == double.class) {
                    x = Double.compare(((double[]) aObj)[i], ((double[]) bObj)[i]);
                } else {
                    long a = ((long[]) aObj)[i];
                    long b = ((long[]) bObj)[i];
                    x = a == b ? 0 : a < b ? -1 : 1;
                }
                if (x != 0) {
                    return x;
                }
            }
        } else {
            Object[] a = (Object[]) aObj;
            Object[] b = (Object[]) bObj;
            for (int i = 0; i < len; i++) {
                int comp = elementType.compare(a[i], b[i]);
                if (comp != 0) {
                    return comp;
                }
            }
        }
        return aLen == bLen ? 0 : aLen < bLen ? -1 : 1;
    }

    @Override
    public int getMemory(Object obj) {
        int size = 64;
        Class<?> type = obj.getClass().getComponentType();
        if (type.isPrimitive()) {
            int len = Array.getLength(obj);
            if (type == boolean.class) {
                size += len;
            } else if (type == byte.class) {
                size += len;
            } else if (type == char.class) {
                size += len * 2;
            } else if (type == short.class) {
                size += len * 2;
            } else if (type == int.class) {
                size += len * 4;
            } else if (type == float.class) {
                size += len * 4;
            } else if (type == double.class) {
                size += len * 8;
            } else if (type == long.class) {
                size += len * 8;
            }
        } else {
            for (Object x : (Object[]) obj) {
                if (x != null) {
                    size += elementType.getMemory(x);
                }
            }
        }
        // we say they are larger, because these objects
        // use quite a lot of disk space
        return size * 2;
    }

    @Override
    public void write(DataBuffer buff, Object obj) {
        Class<?> type = obj.getClass().getComponentType();
        Integer classId = ObjectDataType.getCommonClassId(type);
        if (classId != null) {
            if (type.isPrimitive()) {
                if (type == byte.class) {
                    byte[] data = (byte[]) obj;
                    int len = data.length;
                    if (len <= 15) {
                        buff.put((byte) (TAG_BYTE_ARRAY_0_15 + len));
                    } else {
                        buff.put((byte) TYPE_ARRAY).put((byte) classId.intValue()).putVarInt(len);
                    }
                    buff.put(data);
                    return;
                }
                int len = Array.getLength(obj);
                buff.put((byte) TYPE_ARRAY).put((byte) classId.intValue()).putVarInt(len);
                for (int i = 0; i < len; i++) {
                    if (type == boolean.class) {
                        buff.put((byte) (((boolean[]) obj)[i] ? 1 : 0));
                    } else if (type == char.class) {
                        buff.putChar(((char[]) obj)[i]);
                    } else if (type == short.class) {
                        buff.putShort(((short[]) obj)[i]);
                    } else if (type == int.class) {
                        buff.putInt(((int[]) obj)[i]);
                    } else if (type == float.class) {
                        buff.putFloat(((float[]) obj)[i]);
                    } else if (type == double.class) {
                        buff.putDouble(((double[]) obj)[i]);
                    } else {
                        buff.putLong(((long[]) obj)[i]);
                    }
                }
                return;
            }
            buff.put((byte) TYPE_ARRAY).put((byte) classId.intValue());
        } else {
            buff.put((byte) TYPE_ARRAY).put((byte) -1);
            String c = type.getName();
            ValueString.type.write(buff, c);
        }
        Object[] array = (Object[]) obj;
        int len = array.length;
        buff.putVarInt(len);
        for (Object x : array) {
            elementType.write(buff, x);
        }
    }

    @Override
    public Object read(ByteBuffer buff, int tag) {
        if (tag != TYPE_ARRAY) {
            byte[] data;
            int len = tag - TAG_BYTE_ARRAY_0_15;
            data = DataUtils.newBytes(len);
            buff.get(data);
            return data;
        }
        int ct = buff.get();
        Class<?> clazz;
        Object obj;
        if (ct == -1) {
            String componentType = ValueString.type.read(buff);
            try {
                clazz = Class.forName(componentType);
            } catch (Exception e) {
                throw DataUtils.newIllegalStateException(DataUtils.ERROR_SERIALIZATION, "Could not get class {0}",
                        componentType, e);
            }
        } else {
            clazz = ObjectDataType.COMMON_CLASSES[ct];
        }
        int len = DataUtils.readVarInt(buff);
        try {
            obj = Array.newInstance(clazz, len);
        } catch (Exception e) {
            throw DataUtils.newIllegalStateException(DataUtils.ERROR_SERIALIZATION,
                    "Could not create array of type {0} length {1}", clazz, len, e);
        }
        if (clazz.isPrimitive()) {
            for (int i = 0; i < len; i++) {
                if (clazz == boolean.class) {
                    ((boolean[]) obj)[i] = buff.get() == 1;
                } else if (clazz == byte.class) {
                    ((byte[]) obj)[i] = buff.get();
                } else if (clazz == char.class) {
                    ((char[]) obj)[i] = buff.getChar();
                } else if (clazz == short.class) {
                    ((short[]) obj)[i] = buff.getShort();
                } else if (clazz == int.class) {
                    ((int[]) obj)[i] = buff.getInt();
                } else if (clazz == float.class) {
                    ((float[]) obj)[i] = buff.getFloat();
                } else if (clazz == double.class) {
                    ((double[]) obj)[i] = buff.getDouble();
                } else {
                    ((long[]) obj)[i] = buff.getLong();
                }
            }
        } else {
            Object[] array = (Object[]) obj;
            for (int i = 0; i < len; i++) {
                array[i] = elementType.read(buff);
            }
        }
        return obj;
    }

    @Override
    public void writeValue(DataBuffer buff, Value v) {
        throw newInternalError();
    }

}