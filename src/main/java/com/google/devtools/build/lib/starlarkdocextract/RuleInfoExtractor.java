// Copyright 2024 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.starlarkdocextract;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleClass.Builder.RuleClassType;
import com.google.devtools.build.lib.packages.StarlarkProviderIdentifier;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.starlarkdocextract.StardocOutputProtos.OriginKey;
import com.google.devtools.build.lib.starlarkdocextract.StardocOutputProtos.RuleInfo;

/** API documentation extractor for a rule. */
public final class RuleInfoExtractor {

  /**
   * Extracts API documentation for a rule in the form of a {@link RuleInfo} proto.
   *
   * @param qualifiedName a human-readable name for the rule to use in documentation output; for a
   *     Starlark rule defined in a .bzl file, this would typically be the name under which users of
   *     the module would use the rule
   * @param ruleClass a rule class; if it is a repository rule class, it must be an exported one
   * @throws ExtractionException if extraction failed
   */
  public static RuleInfo buildRuleInfo(
      ExtractorContext context, String qualifiedName, RuleClass ruleClass)
      throws ExtractionException {
    RuleInfo.Builder ruleInfoBuilder = RuleInfo.newBuilder();
    // Record the name under which this symbol is made accessible, which may differ from the
    // symbol's exported name
    ruleInfoBuilder.setRuleName(qualifiedName);
    // ... but record the origin rule key for cross references.
    OriginKey.Builder originKeyBuilder = OriginKey.newBuilder().setName(ruleClass.getName());
    if (ruleClass.isStarlark()) {
      if (ruleClass.getStarlarkExtensionLabel() != null) {
        // Most common case: exported Starlark-defined rule class
        originKeyBuilder.setFile(
            context.labelRenderer().render(ruleClass.getStarlarkExtensionLabel()));
      } else {
        // Unexported Starlark-defined rule class; this only possible for a repository rule. Fall
        // back to the rule definition environment label. (Note that we cannot unconditionally call
        // getRuleDefinitionEnvironmentLabel() because for an analysis test rule class, the rule
        // definition environment label is a dummy value; see b/366027483.)
        originKeyBuilder.setFile(
            context.labelRenderer().render(ruleClass.getRuleDefinitionEnvironmentLabel()));
      }
    } else {
      // Non-Starlark-defined rule class
      originKeyBuilder.setFile("<native>");
    }
    ruleInfoBuilder.setOriginKey(originKeyBuilder.build());

    if (ruleClass.getStarlarkDocumentation() != null) {
      ruleInfoBuilder.setDocString(ruleClass.getStarlarkDocumentation());
    }

    if (ruleClass.getRuleClassType() == RuleClassType.TEST) {
      ruleInfoBuilder.setTest(true);
    }
    if (ruleClass.hasAttr(Rule.IS_EXECUTABLE_ATTRIBUTE_NAME, Type.BOOLEAN)) {
      ruleInfoBuilder.setExecutable(true);
    }

    ruleInfoBuilder.addAttribute(
        AttributeInfoExtractor.IMPLICIT_NAME_ATTRIBUTE_INFO); // name comes first
    AttributeInfoExtractor.addDocumentableAttributes(
        context, ruleClass.getAttributes(), ruleInfoBuilder::addAttribute, "rule " + qualifiedName);
    ImmutableSet<StarlarkProviderIdentifier> advertisedProviders =
        ruleClass.getAdvertisedProviders().getStarlarkProviders();
    if (!advertisedProviders.isEmpty()) {
      ruleInfoBuilder.setAdvertisedProviders(
          ProviderNameGroupExtractor.buildProviderNameGroup(context, advertisedProviders));
    }
    return ruleInfoBuilder.build();
  }

  private RuleInfoExtractor() {}
}
