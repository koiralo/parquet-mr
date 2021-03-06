/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hadoop.hive.ql.io.parquet.convert;

import java.util.List;

import org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.hive.serde2.typeinfo.ListTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.MapTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.StructTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;

import org.apache.parquet.schema.ConversionPatterns;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Type.Repetition;
import org.apache.parquet.schema.Types;

import static org.apache.parquet.schema.LogicalTypeAnnotation.listType;

public class HiveSchemaConverter {

  public static MessageType convert(final List<String> columnNames, final List<TypeInfo> columnTypes) {
    final MessageType schema = new MessageType("hive_schema", convertTypes(columnNames, columnTypes));
    return schema;
  }

  private static Type[] convertTypes(final List<String> columnNames, final List<TypeInfo> columnTypes) {
    if (columnNames.size() != columnTypes.size()) {
      throw new IllegalStateException("Mismatched Hive columns and types. Hive columns names" +
        " found : " + columnNames + " . And Hive types found : " + columnTypes);
    }
    final Type[] types = new Type[columnNames.size()];
    for (int i = 0; i < columnNames.size(); ++i) {
      types[i] = convertType(columnNames.get(i), columnTypes.get(i));
    }
    return types;
  }

  private static Type convertType(final String name, final TypeInfo typeInfo) {
    return convertType(name, typeInfo, Repetition.OPTIONAL);
  }

  private static Type convertType(final String name, final TypeInfo typeInfo, final Repetition repetition) {
    if (typeInfo.getCategory().equals(Category.PRIMITIVE)) {
      if (typeInfo.equals(TypeInfoFactory.stringTypeInfo)) {
        return new PrimitiveType(repetition, PrimitiveTypeName.BINARY, name);
      } else if (typeInfo.equals(TypeInfoFactory.intTypeInfo) ||
          typeInfo.equals(TypeInfoFactory.shortTypeInfo) ||
          typeInfo.equals(TypeInfoFactory.byteTypeInfo)) {
        return new PrimitiveType(repetition, PrimitiveTypeName.INT32, name);
      } else if (typeInfo.equals(TypeInfoFactory.longTypeInfo)) {
        return new PrimitiveType(repetition, PrimitiveTypeName.INT64, name);
      } else if (typeInfo.equals(TypeInfoFactory.doubleTypeInfo)) {
        return new PrimitiveType(repetition, PrimitiveTypeName.DOUBLE, name);
      } else if (typeInfo.equals(TypeInfoFactory.floatTypeInfo)) {
        return new PrimitiveType(repetition, PrimitiveTypeName.FLOAT, name);
      } else if (typeInfo.equals(TypeInfoFactory.booleanTypeInfo)) {
        return new PrimitiveType(repetition, PrimitiveTypeName.BOOLEAN, name);
      } else if (typeInfo.equals(TypeInfoFactory.binaryTypeInfo)) {
        // TODO : binaryTypeInfo is a byte array. Need to map it
        throw new UnsupportedOperationException("Binary type not implemented");
      } else if (typeInfo.equals(TypeInfoFactory.timestampTypeInfo)) {
        throw new UnsupportedOperationException("Timestamp type not implemented");
      } else if (typeInfo.equals(TypeInfoFactory.voidTypeInfo)) {
        throw new UnsupportedOperationException("Void type not implemented");
      } else if (typeInfo.equals(TypeInfoFactory.unknownTypeInfo)) {
        throw new UnsupportedOperationException("Unknown type not implemented");
      } else {
        throw new IllegalArgumentException("Unknown type: " + typeInfo);
      }
    } else if (typeInfo.getCategory().equals(Category.LIST)) {
      return convertArrayType(name, (ListTypeInfo) typeInfo);
    } else if (typeInfo.getCategory().equals(Category.STRUCT)) {
      return convertStructType(name, (StructTypeInfo) typeInfo);
    } else if (typeInfo.getCategory().equals(Category.MAP)) {
      return convertMapType(name, (MapTypeInfo) typeInfo);
    } else if (typeInfo.getCategory().equals(Category.UNION)) {
      throw new UnsupportedOperationException("Union type not implemented");
    } else {
      throw new IllegalArgumentException("Unknown type: " + typeInfo);
    }
  }

  // An optional group containing a repeated anonymous group "bag", containing
  // 1 anonymous element "array_element"
  private static GroupType convertArrayType(final String name, final ListTypeInfo typeInfo) {
    final TypeInfo subType = typeInfo.getListElementTypeInfo();
    return listWrapper(name, listType(), new GroupType(Repetition.REPEATED,
        ParquetHiveSerDe.ARRAY.toString(), convertType("array_element", subType)));
  }

  // An optional group containing multiple elements
  private static GroupType convertStructType(final String name, final StructTypeInfo typeInfo) {
    final List<String> columnNames = typeInfo.getAllStructFieldNames();
    final List<TypeInfo> columnTypes = typeInfo.getAllStructFieldTypeInfos();
    return new GroupType(Repetition.OPTIONAL, name, convertTypes(columnNames, columnTypes));

  }

  // An optional group containing a repeated anonymous group "map", containing
  // 2 elements: "key", "value"
  private static GroupType convertMapType(final String name, final MapTypeInfo typeInfo) {
    final Type keyType = convertType(ParquetHiveSerDe.MAP_KEY.toString(),
        typeInfo.getMapKeyTypeInfo(), Repetition.REQUIRED);
    final Type valueType = convertType(ParquetHiveSerDe.MAP_VALUE.toString(),
        typeInfo.getMapValueTypeInfo());
    return ConversionPatterns.mapType(Repetition.OPTIONAL, name, keyType, valueType);
  }

  private static GroupType listWrapper(final String name, final LogicalTypeAnnotation logicalTypeAnnotation,
      final GroupType groupType) {
    return Types.optionalGroup().addField(groupType).as(logicalTypeAnnotation).named(name);
  }
}
