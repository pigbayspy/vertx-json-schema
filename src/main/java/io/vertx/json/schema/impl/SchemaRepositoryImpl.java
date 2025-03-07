package io.vertx.json.schema.impl;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.*;

import java.util.*;

public class SchemaRepositoryImpl implements SchemaRepository {

  private static final List<String> IGNORE_KEYWORD = Arrays.asList(
    "id",
    "$id",
    "$ref",
    "$schema",
    "$anchor",
    "$vocabulary",
    "$comment",
    "default",
    "enum",
    "const",
    "required",
    "type",
    "maximum",
    "minimum",
    "exclusiveMaximum",
    "exclusiveMinimum",
    "multipleOf",
    "maxLength",
    "minLength",
    "pattern",
    "format",
    "maxItems",
    "minItems",
    "uniqueItems",
    "maxProperties",
    "minProperties");

  private static final List<String> SCHEMA_ARRAY_KEYWORD = Arrays.asList(
    "prefixItems",
    "items",
    "allOf",
    "anyOf",
    "oneOf");

  private static final List<String> SCHEMA_MAP_KEYWORD = Arrays.asList(
    "$defs",
    "definitions",
    "properties",
    "patternProperties",
    "dependentSchemas");

  private static final List<String> SCHEMA_KEYWORD = Arrays.asList(
    "additionalItems",
    "unevaluatedItems",
    "items",
    "contains",
    "additionalProperties",
    "unevaluatedProperties",
    "propertyNames",
    "not",
    "if",
    "then",
    "else"
  );

  private final Map<String, JsonSchema> lookup = new HashMap<>();

  private final JsonSchemaOptions options;
  private final URL baseUri;

  public SchemaRepositoryImpl(JsonSchemaOptions options) {
    this.options = options;
    Objects.requireNonNull(options, "'options' cannot be null");
    Objects.requireNonNull(options.getBaseUri(), "'options.baseUri' cannot be null");
    this.baseUri = new URL(options.getBaseUri());
  }

  @Override
  public SchemaRepository dereference(JsonSchema schema) throws SchemaException {
    dereference(lookup, schema, baseUri, "", true);
    return this;
  }

  @Override
  public SchemaRepository dereference(String uri, JsonSchema schema) throws SchemaException {
    dereference(lookup, schema, new URL(uri, options.getBaseUri()), "", true);
    return this;
  }

  @Override
  public Validator validator(JsonSchema schema) {
    return new SchemaValidatorImpl(schema, options, Collections.unmodifiableMap(lookup));
  }

  @Override
  public Validator validator(String ref) {
    // resolve the pointer to an absolute path
    final URL url = new URL(ref, baseUri);
    final String uri = url.href();
    if (lookup.containsKey(uri)) {
      return new SchemaValidatorImpl(uri, options, Collections.unmodifiableMap(lookup));
    }
    throw new IllegalArgumentException("Unknown $ref: " + ref);
  }

  @Override
  public Validator validator(JsonSchema schema, JsonSchemaOptions options) {
    final JsonSchemaOptions config;
    if (options.getBaseUri() == null) {
      // add the default base if missing
      config = new JsonSchemaOptions(options)
        .setBaseUri(options.getBaseUri());
    } else {
      config = options;
    }
    return new SchemaValidatorImpl(schema, config, Collections.unmodifiableMap(lookup));
  }

  @Override
  public Validator validator(String ref, JsonSchemaOptions options) {
    final JsonSchemaOptions config;
    if (options.getBaseUri() == null) {
      // add the default base if missing
      config = new JsonSchemaOptions(options)
        .setBaseUri(options.getBaseUri());
    } else {
      config = options;
    }

    // resolve the pointer to an absolute path
    final URL url = new URL(ref, baseUri);
    final String uri = url.href();
    if (lookup.containsKey(uri)) {
      return new SchemaValidatorImpl(uri, config, Collections.unmodifiableMap(lookup));
    }
    throw new IllegalArgumentException("Unknown $ref: " + ref);
  }

  @Override
  public JsonObject resolve(JsonSchema schema) {
    // this will perform a dereference of the given schema
    final Map<String, JsonSchema> lookup = new HashMap<>(Collections.unmodifiableMap(this.lookup));
    // the deference will ensure that there are no cyclic references
    // and the given schema is valid to resolved if needed
    dereference(lookup, schema, baseUri, "", true);
    return Ref.resolve(lookup, baseUri, schema);
  }

  @Override
  public JsonObject resolve(String ref) {
    // resolve the pointer to an absolute path
    final URL url = new URL(ref, baseUri);
    final String uri = url.href();
    if (lookup.containsKey(uri)) {
      return Ref.resolve(Collections.unmodifiableMap(lookup), baseUri, lookup.get(uri));
    }
    throw new IllegalArgumentException("Unknown $ref: " + ref);
  }

  @Override
  public JsonSchema find(String pointer) {
    // resolve the pointer to an absolute path
    final URL url = new URL(pointer, baseUri);
    return lookup.get(url.href());
  }

  static void dereference(Map<String, JsonSchema> lookup, JsonSchema schema, URL baseURI, String basePointer, boolean schemaRoot) {
    if (schema == null) {
      return;
    }

    if (!(schema instanceof BooleanSchema)) {
      // This addresses the Unknown Keyword requirements, non sub-schema's with $id are to ignore the
      // given $id as it could collide with existing resolved schemas
      final String id = schemaRoot ? schema.get("$id", schema.get("id")) : null;
      if (Utils.Objects.truthy(id)) {
        final URL url = new URL(id, baseURI.href());
        if (url.fragment().length() > 1) {
          assert !lookup.containsKey(url.href());
          lookup.put(url.href(), schema);
        } else {
          url.anchor(""); // normalize hash https://url.spec.whatwg.org/#dom-url-hash
          if ("".equals(basePointer)) {
            baseURI = url;
          } else {
            dereference(lookup, schema, baseURI, "", schemaRoot);
          }
        }
      }
    }

    // compute the schema's URI and add it to the mapping.
    final String schemaURI = baseURI.href() + (Utils.Objects.truthy(basePointer) ? '#' + basePointer : "");
    if (lookup.containsKey(schemaURI)) {
      JsonSchema existing = lookup.get(schemaURI);
      // this schema has been processed already, skip, this is the same behavior of ajv the most complete
      // validator to my knowledge. This addresses the case where extra $id's are added and would be double
      // referenced, yet, it would be ok as they are the same sub schema
      if (existing.equals(schema)) {
        return;
      }
      throw new SchemaException(schema, "Duplicate schema URI \"" + schemaURI + "\".");
    }
    lookup.put(schemaURI, schema);

    // exit early if this is a boolean schema.
    if (schema instanceof BooleanSchema) {
      return;
    }

    // set the schema's absolute URI.
    if (!schema.containsKey("__absolute_uri__")) {
      schema.annotate("__absolute_uri__", schemaURI);
    }

    // if a $ref is found, resolve it's absolute URI.
    if (schema.containsKey("$ref") && !schema.containsKey("__absolute_ref__")) {
      final URL url = new URL(schema.get("$ref"), baseURI.href());
      url.anchor(url.fragment()); // normalize hash https://url.spec.whatwg.org/#dom-url-hash
      schema.annotate("__absolute_ref__", url.href());
    }

    // if a $recursiveRef is found, resolve it's absolute URI.
    if (schema.containsKey("$recursiveRef") && !schema.containsKey("__absolute_recursive_ref__")) {
      final URL url = new URL(schema.get("$recursiveRef"), baseURI.href());
      url.anchor(url.fragment()); // normalize hash https://url.spec.whatwg.org/#dom-url-hash
      schema.annotate("__absolute_recursive_ref__", url.href());
    }

    // if an $anchor is found, compute it's URI and add it to the mapping.
    if (schema.containsKey("$anchor")) {
      final URL url = new URL("#" + schema.<String>get("$anchor"), baseURI);
      assert !lookup.containsKey(url.href());
      lookup.put(url.href(), schema);
    }

    // process subschemas.
    for (String key : schema.fieldNames()) {
      if (IGNORE_KEYWORD.contains(key)) {
        continue;
      }

      final String keyBase = basePointer + "/" + Utils.Pointers.encode(key);
      final Object subSchema = schema.get(key);

      if (subSchema instanceof JsonArray) {
        if (SCHEMA_ARRAY_KEYWORD.contains(key)) {
          for (int i = 0; i < ((JsonArray) subSchema).size(); i++) {
            dereference(
              lookup,
              Utils.Schemas.wrap((JsonArray) subSchema, i),
              baseURI,
              keyBase + "/" + i,
              false);
          }
        }
      } else if (SCHEMA_MAP_KEYWORD.contains(key)) {
        for (String subKey : ((JsonObject) subSchema).fieldNames()) {
          dereference(
            lookup,
            Utils.Schemas.wrap((JsonObject) subSchema, subKey),
            baseURI,
            keyBase + "/" + Utils.Pointers.encode(subKey),
            true);
        }
      } else if (subSchema instanceof Boolean) {
        dereference(
          lookup,
          JsonSchema.of((Boolean) subSchema),
          baseURI,
          keyBase,
          SCHEMA_KEYWORD.contains(key));
      } else if (subSchema instanceof JsonObject) {
        dereference(
          lookup,
          JsonSchema.of((JsonObject) subSchema),
          baseURI,
          keyBase,
          SCHEMA_KEYWORD.contains(key));
      }
    }
  }
}
