<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE xsl:stylesheet [
    <!ENTITY typeahead  "http://graphity.org/typeahead#">
    <!ENTITY ac         "https://w3id.org/atomgraph/client#">
    <!ENTITY rdf        "http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <!ENTITY rdfs       "http://www.w3.org/2000/01/rdf-schema#">
    <!ENTITY owl        "http://www.w3.org/2002/07/owl#">
    <!ENTITY sparql     "http://www.w3.org/2005/sparql-results#">
    <!ENTITY xsd        "http://www.w3.org/2001/XMLSchema#">
    <!ENTITY sd         "http://www.w3.org/ns/sparql-service-description#">
    <!ENTITY dct        "http://purl.org/dc/terms/">
    <!ENTITY foaf       "http://xmlns.com/foaf/0.1/">
    <!ENTITY sioc       "http://rdfs.org/sioc/ns#">
]>
<xsl:stylesheet
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
xmlns:ixsl="http://saxonica.com/ns/interactiveXSLT"
xmlns:prop="http://saxonica.com/ns/html-property"
xmlns:style="http://saxonica.com/ns/html-style-property"
xmlns:xs="http://www.w3.org/2001/XMLSchema"
xmlns:typeahead="&typeahead;"
xmlns:ac="&ac;"
xmlns:rdf="&rdf;"
xmlns:rdfs="&rdfs;"
xmlns:owl="&owl;"
xmlns:sparql="&sparql;"
xmlns:sd="&sd;"
xmlns:dct="&dct;"
xmlns:foaf="&foaf;"
xmlns:sioc="&sioc;"
xmlns:xhtml="http://www.w3.org/1999/xhtml"
exclude-result-prefixes="xs prop"
extension-element-prefixes="ixsl"
version="2.0"
>
        
    <xsl:template match="*[*][@rdf:about] | *[*][@rdf:nodeID]" mode="ac:TypeaheadOptionMode">
        <xsl:param name="class" as="xs:string?"/>
        <xsl:param name="query" as="xs:string"/>
        <xsl:param name="name" as="xs:string"/>
        <xsl:variable name="label" as="xs:string">
            <xsl:apply-templates select="." mode="ac:label"/>
        </xsl:variable>

        <li>
            <xsl:if test="$class">
                <xsl:attribute name="class"><xsl:value-of select="$class"/></xsl:attribute>
            </xsl:if>
            
            <input type="hidden" name="{$name}" value="{@rdf:about}"/>

            <a title="{@rdf:about}">
                <xsl:choose>
                    <xsl:when test="contains(lower-case($label), lower-case($query))">
                        <xsl:variable name="query-start-pos" select="string-length(substring-before(upper-case($label), upper-case($query))) + 1"/>
                        <xsl:variable name="query-end-pos" select="string-length($label) - string-length(substring-after(upper-case($label), upper-case($query))) + 1"/>

                        <xsl:if test="$query-start-pos &gt; 0">
                            <xsl:value-of select="substring($label, 1, $query-start-pos - 1)"/>
                        </xsl:if>
                        <strong>
                            <xsl:value-of select="substring($label, $query-start-pos, string-length($query))"/>
                        </strong>
                        <xsl:value-of select="substring($label, $query-end-pos)"/>
                        <xsl:text> </xsl:text>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$label"/>
                    </xsl:otherwise>
                </xsl:choose>
                <span class="pull-right" style="font-size: smaller;">
                    <xsl:for-each select="rdf:type/@rdf:resource">
                        <xsl:apply-templates select="." mode="ac:ObjectLabelMode"/>
                        <xsl:if test="position() != last()">
                            <xsl:text> </xsl:text>
                        </xsl:if>
                    </xsl:for-each>
                </span>
            </a>
        </li>
    </xsl:template>
    
    <!-- NAMED TEMPLATES -->

    <xsl:template name="typeahead:load-xml">
        <xsl:param name="element" as="element()"/>
        <xsl:param name="uri" as="xs:anyURI"/>
        <xsl:param name="query" as="xs:string"/>
        <xsl:param name="js-function" as="xs:string"/>
        <xsl:param name="callback"/>
        <!-- if the value hasn't changed during the delay -->
        <xsl:if test="$query = $element/@prop:value">
            <xsl:value-of select="ixsl:call(ixsl:window(), $js-function, ixsl:event(), $uri, $callback)"/>
        </xsl:if>
    </xsl:template>

    <xsl:template name="typeahead:process">
        <xsl:param name="menu" as="element()"/>
        <xsl:param name="items" as="element()*"/>
        <xsl:param name="element" as="element()"/>
        <xsl:param name="name" as="xs:string"/>

        <xsl:choose>
            <xsl:when test="$items">
                <xsl:call-template name="typeahead:render">
                    <xsl:with-param name="menu" select="$menu"/>
                    <xsl:with-param name="items" select="$items"/>
                    <xsl:with-param name="element" select="$element"/>
                    <xsl:with-param name="name" select="$name"/>
                </xsl:call-template>
                
                <xsl:call-template name="typeahead:show">
                    <xsl:with-param name="element" select="$element"/>
                    <xsl:with-param name="menu" select="$menu"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:call-template name="typeahead:hide">
                    <xsl:with-param name="menu" select="$menu"/>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="typeahead:render">
        <xsl:param name="menu" as="element()"/>
        <xsl:param name="items" as="element()*"/>
        <xsl:param name="element" as="element()"/>
        <xsl:param name="name" as="xs:string"/>
        
        <xsl:result-document href="#{$menu/@id}" method="ixsl:replace-content">
            <xsl:apply-templates select="$items" mode="ac:TypeaheadOptionMode">
                <xsl:with-param name="query" select="$element/@prop:value"/>
                <xsl:with-param name="name" select="$name"/>
                <xsl:sort select="rdfs:label[1]"/>
                <xsl:sort select="dct:title[1]"/>
                <xsl:sort select="foaf:name[1]"/>
                <xsl:sort select="foaf:nick[1]"/>
                <xsl:sort select="sioc:name[1]"/>
                <xsl:sort select="@rdf:about"/>
            </xsl:apply-templates>
        </xsl:result-document>
    </xsl:template>
    
    <xsl:template name="typeahead:show">
        <xsl:param name="element" as="element()"/>
        <xsl:param name="menu" as="element()"/>
        
        <xsl:for-each select="$menu">
            <ixsl:set-attribute name="style:display" select="'block'"/>
            <ixsl:set-attribute name="style:top" select="concat($element/@prop:offsetTop + $element/@prop:offsetHeight, 'px')"/>
            <ixsl:set-attribute name="style:left" select="concat($element/@prop:offsetLeft, 'px')"/>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="typeahead:hide">
        <xsl:param name="menu" as="element()"/>

        <xsl:for-each select="$menu">
            <ixsl:set-attribute name="style:display" select="'none'"/>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template name="typeahead:selection-up">
        <xsl:param name="menu" as="element()"/>
        
        <xsl:choose>
            <xsl:when test="$menu/li[tokenize(@class, ' ') = 'active']">
                <xsl:for-each select="$menu/li[tokenize(@class, ' ') = 'active']">
                    <xsl:if test="preceding-sibling::li">
                        <ixsl:set-attribute name="class" select="string-join(tokenize(@class, ' ')[not(. = 'active')], ' ')"/>
                        <xsl:for-each select="preceding-sibling::li[1]">
                            <ixsl:set-attribute name="class" select="concat(@class, ' ', 'active')"/>
                            <xsl:variable name="menu-scroll-top" select="ixsl:get($menu, 'scrollTop')" as="xs:double"/>
                            <xsl:variable name="offset-top" select="ixsl:get(., 'offsetTop')" as="xs:double"/>
                            <xsl:if test="$offset-top &lt; $menu-scroll-top">
                                <ixsl:set-property name="scrollTop" object="$menu" select="$offset-top"/>
                            </xsl:if>
                        </xsl:for-each>
                    </xsl:if>
                </xsl:for-each>
            </xsl:when>
            <xsl:otherwise>
                <xsl:for-each select="$menu/li[last()]">
                    <ixsl:set-attribute name="class" select="concat(@class, ' ', 'active')"/>
                </xsl:for-each>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="typeahead:selection-down">
        <xsl:param name="menu" as="element()"/>

        <xsl:choose>
            <xsl:when test="$menu/li[tokenize(@class, ' ') = 'active']">
                <xsl:for-each select="$menu/li[tokenize(@class, ' ') = 'active']">
                    <xsl:if test="following-sibling::li">
                        <ixsl:set-attribute name="class" select="string-join(tokenize(@class, ' ')[not(. = 'active')], ' ')"/>
                        <xsl:for-each select="following-sibling::li[1]">
                            <ixsl:set-attribute name="class" select="concat(@class, ' ', 'active')"/>
                            <xsl:variable name="menu-scroll-top" select="ixsl:get($menu, 'scrollTop')" as="xs:double"/>
                            <xsl:variable name="menu-offset-height" select="ixsl:get($menu, 'offsetHeight')" as="xs:double"/>
                            <xsl:variable name="offset-top" select="ixsl:get(., 'offsetTop')" as="xs:double"/>
                            <xsl:variable name="offset-height" select="ixsl:get(., 'offsetHeight')" as="xs:double"/>
                            <xsl:if test="($offset-top + $offset-height) &gt; ($menu-scroll-top + $menu-offset-height)">
                                <ixsl:set-property name="scrollTop" object="$menu" select="($offset-top + $offset-height) - $menu-offset-height"/>
                            </xsl:if>
                        </xsl:for-each>
                    </xsl:if>
                </xsl:for-each>
            </xsl:when>
            <xsl:otherwise>
                <xsl:for-each select="$menu/li[1]">
                    <ixsl:set-attribute name="class" select="concat(@class, ' ', 'active')"/>
                </xsl:for-each>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
</xsl:stylesheet>