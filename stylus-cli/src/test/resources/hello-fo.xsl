<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
  <xsl:template match="/Greeting">
    <fo:root>
      <fo:layout-master-set>
        <fo:simple-page-master master-name="p" page-height="297mm" page-width="210mm" margin="20mm">
          <fo:region-body/>
        </fo:simple-page-master>
      </fo:layout-master-set>
      <fo:page-sequence master-reference="p">
        <fo:flow flow-name="xsl-region-body">
          <fo:block font-size="14pt"><xsl:value-of select="Text"/></fo:block>
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
</xsl:stylesheet>
