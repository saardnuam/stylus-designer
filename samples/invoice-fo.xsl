<?xml version="1.0" encoding="UTF-8"?>
<!-- Banded invoice sample: page header/footer, customer group, order group, line-item table. -->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

  <xsl:output method="xml" indent="yes"/>
  <xsl:param name="reportTitle">Quarterly Invoice Summary</xsl:param>

  <xsl:template match="/InvoiceData">
    <fo:root>
      <fo:layout-master-set>
        <fo:simple-page-master master-name="a4"
                               page-height="297mm" page-width="210mm"
                               margin-top="15mm" margin-bottom="15mm"
                               margin-left="20mm" margin-right="20mm">
          <fo:region-body margin-top="12mm" margin-bottom="12mm"/>
          <fo:region-before extent="10mm"/>
          <fo:region-after extent="10mm"/>
        </fo:simple-page-master>
      </fo:layout-master-set>

      <fo:page-sequence master-reference="a4">
        <fo:static-content flow-name="xsl-region-before">
          <fo:block font-size="8pt" text-align-last="justify">
            <xsl:value-of select="Company/Name"/>
            <fo:leader leader-pattern="space"/>
            Page <fo:page-number/>
          </fo:block>
        </fo:static-content>
        <!-- Portable footer: fo:page-number-citation-last is rejected by BIP 12c
             ("not supported yet", capability matrix doc 07) — plain page-number here
             keeps this sample runnable on both engines. -->
        <fo:static-content flow-name="xsl-region-after">
          <fo:block font-size="8pt" text-align="center">
            Confidential — Finance · Page <fo:page-number/>
          </fo:block>
        </fo:static-content>

        <fo:flow flow-name="xsl-region-body">
          <fo:block font-size="18pt" font-weight="bold" text-align="center"
                    space-after="4mm" border-bottom="2pt solid black" padding-bottom="2mm">
            <xsl:value-of select="$reportTitle"/>
          </fo:block>

          <xsl:for-each select="Customers/Customer">
            <xsl:sort select="Name"/>
            <fo:block space-before="5mm" font-size="13pt" font-weight="bold" color="#5145E8">
              <xsl:value-of select="Name"/> · <xsl:value-of select="Region"/>
            </fo:block>

            <xsl:for-each select="Orders/Order">
              <fo:block space-before="2mm" font-size="10pt" font-weight="bold">
                Order <xsl:value-of select="OrderID"/> · <xsl:value-of select="OrderDate"/>
              </fo:block>

              <fo:table table-layout="fixed" width="100%" space-before="1mm">
                <fo:table-column column-width="proportional-column-width(2.4)"/>
                <fo:table-column column-width="proportional-column-width(0.7)"/>
                <fo:table-column column-width="proportional-column-width(1)"/>
                <fo:table-column column-width="proportional-column-width(1)"/>
                <fo:table-header>
                  <fo:table-row font-size="8pt" font-weight="bold">
                    <fo:table-cell border-bottom="0.5pt solid #999"><fo:block>PRODUCT</fo:block></fo:table-cell>
                    <fo:table-cell border-bottom="0.5pt solid #999"><fo:block text-align="right">QTY</fo:block></fo:table-cell>
                    <fo:table-cell border-bottom="0.5pt solid #999"><fo:block text-align="right">UNIT PRICE</fo:block></fo:table-cell>
                    <fo:table-cell border-bottom="0.5pt solid #999"><fo:block text-align="right">AMOUNT</fo:block></fo:table-cell>
                  </fo:table-row>
                </fo:table-header>
                <fo:table-body>
                  <xsl:for-each select="LineItem">
                    <fo:table-row font-size="9pt">
                      <xsl:if test="Amount &gt; 1000">
                        <xsl:attribute name="font-weight">bold</xsl:attribute>
                        <xsl:attribute name="color">#C0392B</xsl:attribute>
                      </xsl:if>
                      <fo:table-cell><fo:block><xsl:value-of select="Product"/></fo:block></fo:table-cell>
                      <fo:table-cell><fo:block text-align="right"><xsl:value-of select="Qty"/></fo:block></fo:table-cell>
                      <fo:table-cell><fo:block text-align="right"><xsl:value-of select="format-number(UnitPrice, '#,##0.00')"/></fo:block></fo:table-cell>
                      <fo:table-cell><fo:block text-align="right"><xsl:value-of select="format-number(Amount, '#,##0.00')"/></fo:block></fo:table-cell>
                    </fo:table-row>
                  </xsl:for-each>
                </fo:table-body>
              </fo:table>

              <fo:block font-size="9pt" text-align="right" space-before="1mm">
                Order subtotal <xsl:value-of select="format-number(sum(LineItem/Amount), '#,##0.00')"/>
              </fo:block>
            </xsl:for-each>

            <fo:block font-size="10pt" font-weight="bold" text-align="right"
                      space-before="2mm" border-top="1pt solid black" padding-top="1mm">
              Total due <xsl:value-of select="format-number(sum(Orders/Order/LineItem/Amount), '#,##0.00')"/>
            </fo:block>
          </xsl:for-each>

          <fo:block id="last"/>
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
</xsl:stylesheet>
