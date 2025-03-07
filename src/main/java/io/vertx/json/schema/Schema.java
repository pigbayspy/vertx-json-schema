/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.json.schema;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.json.pointer.JsonPointer;

/**
 * Interface representing a <a href="https://json-schema.org/">Json Schema</a> <br/>
 * <p>
 * A schema could have two states: <br/>
 * <ul>
 *   <li>Synchronous: The validators tree can provide a synchronous validation, so you can validate your json both using {@link this#validateSync(Object)} and {@link this#validateAsync(Object)}</li>
 *   <li>Asynchronous: One or more branches of the validator tree requires an asynchronous validation, so you must use {@link this#validateAsync(Object)} to validate your json. If you use {@link this#validateSync(Object)} it will throw a {@link NoSyncValidationException}</li>
 * </ul>
 * <p>
 * To check the schema state you can use method {@link this#isSync()}. Note that invoking {@link #validateAsync(Object)} generally doesn't have any additional overhead than invoking {@link #validateSync(Object)}. <br/>
 * The schema can mutate the state in time, e.g. if you have a schema that is asynchronous because of a {@code $ref},
 * after the first validation the external schema is cached inside {@link SchemaRouter} and this schema will switch to synchronous state<br/>
 * @deprecated users should migrate to the new validator
 */
@Deprecated
@VertxGen
public interface Schema {

  /**
   * Validate the json performing an asynchronous validation.<br/>
   * <p>
   * Note: If the schema is synchronous, this method will call internally {@link this#validateSync(Object)}
   *
   * @param json input to validate
   * @return a failed future with {@link ValidationException} if json doesn't match the schema, otherwise a succeeded future.
   */
  Future<Void> validateAsync(Object json);

  /**
   * Validate the json performing a synchronous validation. Throws a {@link ValidationException} if json doesn't match the schema.<br/>
   *
   * @param json input to validate
   * @throws ValidationException       if the input doesn't match the schema
   * @throws NoSyncValidationException If the schema cannot perform a synchronous validation
   */
  void validateSync(Object json) throws ValidationException, NoSyncValidationException;

  /**
   * @return scope of this schema
   */
  JsonPointer getScope();

  /**
   * @return Json representation of the schema
   */
  Object getJson();

  /**
   * @return true if this validator can provide a synchronous validation.
   */
  boolean isSync();

}
