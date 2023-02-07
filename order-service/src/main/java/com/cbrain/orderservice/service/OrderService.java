package com.cbrain.orderservice.service;

import com.cbrain.orderservice.dto.OrderDetailsDto;
import com.cbrain.orderservice.dto.OrderRequest;
import com.cbrain.orderservice.model.Order;
import com.cbrain.orderservice.model.OrderDetails;
import com.cbrain.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    public void placeOrder(OrderRequest orderRequest) {
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

        orderRepository.save(order);

    }

    private OrderDetails mapToDto(OrderDetailsDto orderDetailsDto) {
        OrderDetails orderDetails = new OrderDetails();
        orderDetails.setPrice(orderDetailsDto.getPrice());
        orderDetails.setQuantity(orderDetailsDto.getQuantity());
        orderDetails.setSkuCode(orderDetailsDto.getSkuCode());
        return orderDetails;
    }
}
