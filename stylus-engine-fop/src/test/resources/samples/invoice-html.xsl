<?xml version="1.0" encoding="UTF-8"?>
<!-- Web-mode sample (F-4.2): unlimited-width HTML output from the same data. -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="html" indent="yes"/>
  <xsl:param name="reportTitle">Quarterly Invoice Summary</xsl:param>

  <xsl:template match="/InvoiceData">
    <html>
      <head><title><xsl:value-of select="$reportTitle"/></title></head>
      <body>
        <h1><xsl:value-of select="$reportTitle"/></h1>
        <p><xsl:value-of select="Company/Name"/> — <xsl:value-of select="Company/Address"/></p>
        <xsl:for-each select="Customers/Customer">
          <h2><xsl:value-of select="Name"/> (<xsl:value-of select="Region"/>)</h2>
          <xsl:for-each select="Orders/Order">
            <h3>Order <xsl:value-of select="OrderID"/> — <xsl:value-of select="OrderDate"/></h3>
            <table border="1">
              <tr><th>Product</th><th>Qty</th><th>Unit price</th><th>Amount</th></tr>
              <xsl:for-each select="LineItem">
                <tr>
                  <td><xsl:value-of select="Product"/></td>
                  <td><xsl:value-of select="Qty"/></td>
                  <td><xsl:value-of select="UnitPrice"/></td>
                  <td><xsl:value-of select="Amount"/></td>
                </tr>
              </xsl:for-each>
            </table>
          </xsl:for-each>
        </xsl:for-each>
      </body>
    </html>
  </xsl:template>
</xsl:stylesheet>
