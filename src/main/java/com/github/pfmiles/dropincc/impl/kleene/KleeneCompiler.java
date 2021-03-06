/*******************************************************************************
 * Copyright (c) 2012 pf_miles.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     pf_miles - initial API and implementation
 ******************************************************************************/
package com.github.pfmiles.dropincc.impl.kleene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.pfmiles.dropincc.CC;
import com.github.pfmiles.dropincc.DropinccException;
import com.github.pfmiles.dropincc.Element;
import com.github.pfmiles.dropincc.Grule;
import com.github.pfmiles.dropincc.TokenDef;
import com.github.pfmiles.dropincc.impl.Alternative;
import com.github.pfmiles.dropincc.impl.EleType;
import com.github.pfmiles.dropincc.impl.GruleType;
import com.github.pfmiles.dropincc.impl.TypeMappingParam;
import com.github.pfmiles.dropincc.impl.util.Counter;
import com.github.pfmiles.dropincc.impl.util.Util;

/**
 * Util, to analyze and manipulate kleene rules.
 * 
 * @author pf-miles
 * 
 */
public class KleeneCompiler {

    /**
     * Build kleene node -> corresponding kleene type mapping
     * 
     * @param gruleTypeMapping
     * @return
     */
    public static Map<AbstractKleeneNode, KleeneType> buildKleeneTypeMapping(Map<Grule, GruleType> gruleTypeMapping) {
        Map<AbstractKleeneNode, KleeneType> ret = new HashMap<AbstractKleeneNode, KleeneType>();
        Set<Grule> examinedGrules = new HashSet<Grule>();
        Counter kleeneCount = new Counter(0);
        for (Map.Entry<Grule, GruleType> entry : gruleTypeMapping.entrySet()) {
            registerKleenesInGrule(entry.getKey(), kleeneCount, ret, examinedGrules);
        }
        return ret;
    }

    private static void registerKleenesInGrule(Grule grule, Counter kleeneCount, Map<AbstractKleeneNode, KleeneType> ret, Set<Grule> examinedGrules) {
        if (examinedGrules.contains(grule)) {
            return;
        } else {
            examinedGrules.add(grule);
            for (Alternative alt : grule.getAlts()) {
                registerKleenesInElements(alt.getElements(), kleeneCount, ret, examinedGrules);
            }
        }
    }

    private static void registerKleenesInElements(List<Element> elements, Counter kleeneCount, Map<AbstractKleeneNode, KleeneType> kleeneTypeMapping,
            Set<Grule> examinedGrules) {
        for (Element e : elements) {
            Class<?> eleCls = e.getClass();
            if (Grule.class.isAssignableFrom(eleCls)) {
                registerKleenesInGrule((Grule) e, kleeneCount, kleeneTypeMapping, examinedGrules);
            } else if (AbstractKleeneNode.class.isAssignableFrom(eleCls)) {
                // kleeneTypeMapping.containsKey(e) at here should never be
                // true, because kleene nodes have same hashcode/equals methods
                // as Object class.
                if (!kleeneTypeMapping.containsKey(e)) {
                    kleeneTypeMapping.put((AbstractKleeneNode) e, constructKleeneType((AbstractKleeneNode) e, kleeneCount));
                    registerKleenesInElements(((AbstractKleeneNode) e).getElements(), kleeneCount, kleeneTypeMapping, examinedGrules);
                }
            } else if (TokenDef.class.isAssignableFrom(eleCls)) {
                continue;
            } else if (CC.NOTHING.equals(e)) {
                continue;
            } else {
                throw new DropinccException("Unexpected element encountered when register kleene node: " + e);
            }
        }
    }

    private static KleeneType constructKleeneType(AbstractKleeneNode e, Counter kleeneCount) {
        Class<?> eleCls = e.getClass();
        KleeneType ret = null;
        if (KleeneCrossNode.class.isAssignableFrom(eleCls)) {
            ret = new KleeneCrossType(kleeneCount.getCount());
        } else if (KleeneStarNode.class.isAssignableFrom(eleCls)) {
            ret = new KleeneStarType(kleeneCount.getCount());
        } else if (OptionalNode.class.isAssignableFrom(eleCls)) {
            ret = new OptionalType(kleeneCount.getCount());
        } else {
            throw new DropinccException("Unhandled kleene node: " + e);
        }
        kleeneCount.countByOne();
        return ret;
    }

    /**
     * @param typeMappingParam
     * @return
     */
    public static Map<KleeneType, List<EleType>> buildKleeneTypeToNode(TypeMappingParam param) {
        Map<KleeneType, List<EleType>> kleeneTypeToNode = new HashMap<KleeneType, List<EleType>>();
        for (Map.Entry<AbstractKleeneNode, KleeneType> entry : param.getKleeneTypeMapping().entrySet()) {
            kleeneTypeToNode.put(entry.getValue(), resolveCKleeneNode(entry.getKey(), param));
        }
        return kleeneTypeToNode;
    }

    private static List<EleType> resolveCKleeneNode(AbstractKleeneNode node, TypeMappingParam param) {
        List<EleType> content = new ArrayList<EleType>();
        if (node == null || node.getElements() == null || node.getElements().isEmpty()) {
            throw new DropinccException("Cannot create empty kleene node: " + node);
        }
        for (Element e : node.getElements()) {
            EleType t = Util.resolveEleType(e, param);
            if (t == null)
                throw new DropinccException("Could not resolve element type for element: " + e + ", is this element defined in a proper manner?");
            content.add(t);
        }
        return content;
    }

}
