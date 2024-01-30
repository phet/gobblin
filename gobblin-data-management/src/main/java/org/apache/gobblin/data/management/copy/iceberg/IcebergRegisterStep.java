/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gobblin.data.management.copy.iceberg;

import java.io.IOException;
import java.util.Properties;

import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.catalog.TableIdentifier;

import lombok.extern.slf4j.Slf4j;

import org.apache.gobblin.commit.CommitStep;


/**
 * {@link CommitStep} to perform Iceberg registration.
 */
@Slf4j
public class IcebergRegisterStep implements CommitStep {

  // store as string for serializability... TODO: explore whether truly necessary (or we could just as well store as `TableIdentifier`)
  private final String srcTableIdStr; // used merely for naming within trace logging
  private final String destTableIdStr;
  private final TableMetadata readTimeSrcTableMetadata;
  private final TableMetadata justPriorDestTableMetadata;
  private final Properties properties;

  public IcebergRegisterStep(TableIdentifier srcTableId, TableIdentifier destTableId,
      TableMetadata readTimeSrcTableMetadata, TableMetadata justPriorDestTableMetadata,
      Properties properties) {
    this.srcTableIdStr = srcTableId.toString();
    this.destTableIdStr = destTableId.toString();
    this.readTimeSrcTableMetadata = readTimeSrcTableMetadata;
    this.justPriorDestTableMetadata = justPriorDestTableMetadata;
    this.properties = properties;
  }

  @Override
  public boolean isCompleted() throws IOException {
    return false;
  }

  @Override
  public void execute() throws IOException {
    IcebergTable destIcebergTable = IcebergDatasetFinder.createIcebergCatalog(this.properties, IcebergDatasetFinder.CatalogLocation.DESTINATION)
        .openTable(TableIdentifier.parse(destTableIdStr));
    // NOTE: solely by-product of probing table's existence: metadata recorded just prior to reading from source catalog is what's actually used
    TableMetadata unusedNowCurrentDestMetadata = null;
    try {
      unusedNowCurrentDestMetadata = destIcebergTable.accessTableMetadata(); // probe... (first access could throw)
      unusedNowCurrentDestMetadata.uuid(); // access to prevent findbugs "dead store to..." (Dodgy code Warning)
    } catch (IcebergTable.TableNotFoundException tnfe) {
      log.warn("Destination TableMetadata doesn't exist because: " , tnfe);
    }
    // TODO: decide whether helpful to construct a more detailed error message about `justPriorDestTableMetadata` being no-longer current
    destIcebergTable.registerIcebergTable(readTimeSrcTableMetadata, justPriorDestTableMetadata);
  }

  @Override
  public String toString() {
    return String.format("Registering Iceberg Table: {%s} (dest); (src: {%s})", this.destTableIdStr, this.srcTableIdStr);
  }
}
