package com.cbrain.orderservice.service;

import brave.Span;
import brave.Tracer;
import com.cbrain.orderservice.dto.InventoryResponse;
import com.cbrain.orderservice.dto.OrderDetailsDto;
import com.cbrain.orderservice.dto.OrderRequest;
import com.cbrain.orderservice.model.Order;
import com.cbrain.orderservice.model.OrderDetails;
import com.cbrain.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    private final Tracer tracer;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderDetails> orderDetails = orderRequest.getOrderDetailsDtoList()
                .stream()
                .map(this::mapToDto).toList()
                .stream()
                .map(od -> {
                    od.setOrder(order);
                    return od;
                }).collect(Collectors.toList());

        order.setOrderDetailsList(orderDetails);

        List<String> skuCodes = order.getOrderDetailsList().stream()
                .map(OrderDetails::getSkuCode)
                .toList();

        Span inventoryServiceLookup = tracer.nextSpan().name("InventoryServiceLookup");

        try (Tracer.SpanInScope isLookup = tracer.withSpanInScope(inventoryServiceLookup.start())) {

            inventoryServiceLookup.tag("call", "inventory-service");

            // Call Inventory Service, and place order if product is in
            // stock
            InventoryResponse[] inventoryResponsArray = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();

            boolean allProductsInStock = Arrays.stream(inventoryResponsArray)
                    .allMatch(InventoryResponse::isInStock);

            if(allProductsInStock){
                orderRepository.save(order);
                return "Order Placed Successfully";
            } else {
                throw new IllegalArgumentException("Product is not in stock, please try again later");
            }
        } finally {
            inventoryServiceLookup.flush();
        }

    }

    private OrderDetails mapToDto(OrderDetailsDto orderDetailsDto) {
        OrderDetails orderDetails = new OrderDetails();
        orderDetails.setPrice(orderDetailsDto.getPrice());
        orderDetails.setQuantity(orderDetailsDto.getQuantity());
        orderDetails.setSkuCode(orderDetailsDto.getSkuCode());
        return orderDetails;
    }
}
