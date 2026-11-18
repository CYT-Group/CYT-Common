package group.cyt.common.module.adapter.shopify;

/** Vendor documents owned by the Java adapter; referenced through enum constants. */
enum ShopifyQuery {
  ORDER(
"""

query Order($id: ID!) {
  order(id: $id) {
    id name createdAt updatedAt note tags
    displayFinancialStatus displayFulfillmentStatus cancelReason cancelledAt
    totalPriceSet { shopMoney { amount currencyCode } }
    totalShippingPriceSet { shopMoney { amount currencyCode } }
    totalReceivedSet { presentmentMoney { amount currencyCode } }
    totalRefundedSet { shopMoney { amount currencyCode } presentmentMoney { amount currencyCode } }
    customer { id displayName email }
    fulfillments(first: 10) { id status trackingInfo { number company url } }
    shippingAddress { name address1 address2 city province zip countryCodeV2 phone }
    lineItems(first: 50) { edges { node {
      id title quantity sku
      originalUnitPriceSet { shopMoney { amount currencyCode } }
      variant { title }
    } } pageInfo { hasNextPage hasPreviousPage endCursor } }
    transactions(first: 20) { id kind status gateway processedAt paymentId
      amountSet { shopMoney { amount currencyCode } } }
  }
}
"""),
  ORDERS(
"""

query Orders($first: Int!, $query: String, $after: String, $sortKey: OrderSortKeys, $reverse: Boolean) {
  orders(first: $first, query: $query, after: $after, sortKey: $sortKey, reverse: $reverse) {
    edges { node {
      id name createdAt updatedAt note tags
      displayFinancialStatus displayFulfillmentStatus
      cancelReason cancelledAt
      totalPriceSet { shopMoney { amount currencyCode } }
      totalShippingPriceSet { shopMoney { amount currencyCode } }
      totalRefundedSet { shopMoney { amount currencyCode } }
      customer { id displayName email }
      fulfillments(first: 10) { id status trackingInfo { number company url } }
      shippingAddress { name address1 address2 city province zip countryCodeV2 phone }
      lineItems(first: 50) { edges { node {
        id title quantity sku
        originalUnitPriceSet { shopMoney { amount currencyCode } }
        variant { title }
      } } pageInfo { hasNextPage hasPreviousPage endCursor } }
      transactions(first: 20) { id kind status gateway processedAt paymentId
        amountSet { shopMoney { amount currencyCode } } }
    } }
    pageInfo { hasNextPage endCursor }
  }
}
"""),
  ORDER_CANCEL_STATE(
"""
query OrderCancelState($id: ID!) {
  shop { myshopifyDomain }
  order(id: $id) {
    id updatedAt cancelledAt displayFulfillmentStatus displayFinancialStatus
    customer { id }
    fulfillments(first: 10) { status }
    totalReceivedSet { presentmentMoney { amount currencyCode } }
    totalRefundedSet { presentmentMoney { amount currencyCode } }
  }
}
"""),
  ORDER_CANCEL(
"""
mutation OrderCancel($orderId: ID!, $reason: OrderCancelReason!,
    $refundMethod: OrderCancelRefundMethodInput!, $restock: Boolean!, $notifyCustomer: Boolean!,
    $staffNote: String) {
  orderCancel(orderId: $orderId, reason: $reason, refundMethod: $refundMethod,
      restock: $restock, notifyCustomer: $notifyCustomer, staffNote: $staffNote) {
    job { id done }
    orderCancelUserErrors { code message field }
    userErrors { message field }
  }
}
"""),
  ORDER_LINES(
"""
query OrderLines($id: ID!, $after: String!) {
  order(id: $id) {
    id updatedAt
    lineItems(first: 50, after: $after) {
      edges { node { id title quantity sku
        originalUnitPriceSet { shopMoney { amount currencyCode } }
        variant { title }
      } }
      pageInfo { hasNextPage hasPreviousPage endCursor }
    }
  }
}
"""),
  PRODUCTS(
"""

query Products($first: Int!, $query: String, $after: String, $sortKey: ProductSortKeys, $reverse: Boolean!) {
  products(first: $first, query: $query, after: $after, sortKey: $sortKey, reverse: $reverse) {
    edges { node { id title handle status totalInventory updatedAt
      variants(first: 10) { count edges { node { id sku } } } } }
    pageInfo { hasNextPage endCursor }
  }
}
"""),
  PRODUCT(
"""

query Product($id: ID!) {
  product(id: $id) {
    id title handle status descriptionHtml totalInventory updatedAt
    variants(first: 50) { edges { node { id sku price inventoryQuantity } } }
  }
}
"""),
  ORDER_UPDATE(
"""

mutation OrderUpdate($input: OrderInput!) {
  orderUpdate(input: $input) {
    order { id name note tags updatedAt
      shippingAddress { name address1 address2 city province zip countryCodeV2 phone } }
    userErrors { field message }
  }
}
"""),
  REFUND_PREFLIGHT(
"""

query RefundPreflight($id: ID!) {
  order(id: $id) {
    id name displayFinancialStatus
    totalPriceSet { shopMoney { amount currencyCode } }
    totalRefundedSet { shopMoney { amount currencyCode } }
    lineItems(first: 100) { edges { node {
      id quantity currentQuantity
      originalUnitPriceSet { shopMoney { amount currencyCode } }
    } } }
    transactions(first: 20) { id kind status gateway processedAt
      amountSet { shopMoney { amount currencyCode } } }
  }
}
"""),
  REFUND_CREATE(
"""

mutation RefundCreate($input: RefundInput!) {
  refundCreate(input: $input) @idempotent(key: "__KEY__") {
    refund { id createdAt note
      totalRefundedSet { shopMoney { amount currencyCode } } }
    order { id name displayFinancialStatus
      totalRefundedSet { shopMoney { amount currencyCode } } }
    userErrors { field message }
  }
}
"""),
  REFUNDS(
"""

query RefundOrders($first: Int!, $query: String, $after: String) {
  orders(first: $first, query: $query, after: $after, sortKey: UPDATED_AT, reverse: false) {
    edges { node {
      id name createdAt updatedAt displayFinancialStatus
      customer { email displayName }
      totalRefundedSet { shopMoney { amount currencyCode } }
      refunds(first: 20) {
        id createdAt note
        totalRefundedSet { shopMoney { amount currencyCode } }
        refundLineItems(first: 50) { edges { node {
          quantity
          lineItem { sku title }
          subtotalSet { shopMoney { amount currencyCode } }
        } } }
        transactions(first: 10) {
          edges { node {
            id kind status gateway processedAt
            amountSet { shopMoney { amount currencyCode } }
          } }
        }
      }
    } }
    pageInfo { hasNextPage endCursor }
  }
}
"""),
  RETURNS(
"""

query ReturnOrders($first: Int!, $query: String, $after: String) {
  orders(first: $first, query: $query, after: $after, sortKey: UPDATED_AT, reverse: false) {
    edges { node {
      id name createdAt updatedAt displayFinancialStatus
      customer { email displayName }
      returns(first: 3) {
        edges { node {
          id name status createdAt totalQuantity
          returnLineItems(first: 10) { edges { node {
            quantity returnReasonNote
            returnReasonDefinition { handle name }
          } } }
          reverseFulfillmentOrders(first: 1) { edges { node {
            lineItems(first: 10) { edges { node {
              totalQuantity
              fulfillmentLineItem { lineItem { sku title } }
            } } }
          } } }
        } }
      }
    } }
    pageInfo { hasNextPage endCursor }
  }
}
"""),
  INVENTORY(
"""

mutation SetInventory($input: InventorySetQuantitiesInput!) {
  inventorySetQuantities(input: $input) {
    userErrors { field message }
  }
}
"""),
  VARIANT_SKU(
"""
query ReshipVariant($query: String!) {
  productVariants(first: 10, query: $query) { nodes { id sku title } }
}"""),
  ORDER_CREATE(
"""
mutation ReshipCreate($order: OrderCreateOrderInput!, $options: OrderCreateOptionsInput) {
  orderCreate(order: $order, options: $options) {
    order { id name } userErrors { field message }
  }
}""");

  private final String document;

  ShopifyQuery(String document) {
    this.document = document;
  }

  String document() {
    return document;
  }
}
