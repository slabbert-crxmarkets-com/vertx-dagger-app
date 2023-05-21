package com.example.catalog.service;

import com.example.catalog.entity.Item;
import com.example.catalog.repository.ItemRepository;
import com.example.catalog.web.route.dto.CreateItemRequestDto;
import com.example.catalog.web.route.dto.CreateItemResponseDto;
import com.example.catalog.web.route.dto.DeleteOneResponseDto;
import com.example.catalog.web.route.dto.FindAllRequestDto;
import com.example.catalog.web.route.dto.FindAllResponseDto;
import com.example.catalog.web.route.dto.FindOneResponseDto;
import com.example.catalog.web.route.dto.UpdateItemRequestDto;
import com.example.catalog.web.route.dto.UpdateItemResponseDto;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.java.Log;

@Log
@Singleton
class ItemServiceImpl implements ItemService {

  private final Emitter emitter;
  private final ItemRepository itemRepository;

  @Inject
  ItemServiceImpl(Emitter emitter, ItemRepository itemRepository) {
    this.itemRepository = itemRepository;
    this.emitter = emitter;
  }

  @Override
  public Future<FindAllResponseDto> findAll(FindAllRequestDto dto) {
    return itemRepository
        .findAll(0, -1)
        .map(
            items ->
                items.stream()
                    .map(
                        item -> new FindOneResponseDto(item.id(), item.name(), item.priceInCents()))
                    .toList())
        .map(FindAllResponseDto::new);
  }

  @Override
  public Future<CreateItemResponseDto> create(CreateItemRequestDto dto) {
    return itemRepository
        .create(dto.name(), dto.priceInCents())
        .onSuccess(emitter::emit)
        .map(item -> new CreateItemResponseDto(item.id(), item.name(), item.priceInCents()));
  }

  @Override
  public Future<Optional<FindOneResponseDto>> findById(UUID id) {
    return itemRepository
        .findById(id)
        .map(
            maybeItem ->
                maybeItem.map(
                    item -> new FindOneResponseDto(item.id(), item.name(), item.priceInCents())));
  }

  @Override
  public Future<Optional<UpdateItemResponseDto>> update(UUID id, UpdateItemRequestDto dto) {
    return itemRepository
        .update(id, dto.name(), dto.priceInCents())
        .onSuccess(
            success -> {
              if (Boolean.TRUE.equals(success)) {
                emitter.emit(new Item(id, dto.name(), dto.priceInCents()));
              }
            })
        .map(
            success ->
                Boolean.TRUE.equals(success)
                    ? Optional.of(new UpdateItemResponseDto())
                    : Optional.empty());
  }

  @Override
  public Future<Optional<DeleteOneResponseDto>> delete(UUID id) {
    return itemRepository
        .delete(id)
        .map(
            success ->
                Boolean.TRUE.equals(success)
                    ? Optional.of(new DeleteOneResponseDto())
                    : Optional.empty());
  }
}
