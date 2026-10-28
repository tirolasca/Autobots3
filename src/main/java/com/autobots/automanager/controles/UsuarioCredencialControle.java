package com.autobots.automanager.controles;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Date;
import java.util.List;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.autobots.automanager.entidades.Credencial;
import com.autobots.automanager.entidades.Usuario;
import com.autobots.automanager.modelo.CredencialAtualizador;
import com.autobots.automanager.repositorios.UsuarioRepositorio;

/**
 * Credenciais de acesso do usuário. No corpo das requisições, a propriedade
 * "tipo" escolhe o subtipo: USUARIO_SENHA (nomeUsuario e senha) ou
 * CODIGO_BARRA (codigo). A senha nunca é devolvida nas respostas.
 */
@RestController
@Transactional
@RequestMapping("/usuario/{usuarioId}/credencial")
public class UsuarioCredencialControle {
	private final UsuarioRepositorio repositorio;
	private final CredencialAtualizador atualizador = new CredencialAtualizador();

	public UsuarioCredencialControle(UsuarioRepositorio repositorio) {
		this.repositorio = repositorio;
	}

	private Usuario obterDonoOuFalhar(long donoId) {
		return repositorio.findById(donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
	}

	private Credencial buscar(Usuario dono, long id) {
		for (Credencial item : dono.getCredenciais()) {
			if (item.getId() != null && item.getId() == id) {
				return item;
			}
		}
		return null;
	}

	private EntityModel<Credencial> montarModelo(long donoId, Credencial item) {
		Link self = linkTo(methodOn(UsuarioCredencialControle.class).obterCredencial(donoId, item.getId()))
				.withSelfRel();
		Link lista = linkTo(methodOn(UsuarioCredencialControle.class).obterCredenciais(donoId))
				.withRel("credenciais");
		Link dono = linkTo(methodOn(UsuarioControle.class).obterUsuario(donoId)).withRel("usuario");
		return EntityModel.of(item, self, lista, dono);
	}

	@GetMapping("/{id}")
	public ResponseEntity<EntityModel<Credencial>> obterCredencial(@PathVariable long usuarioId,
			@PathVariable long id) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		Credencial item = buscar(dono, id);
		if (item == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(montarModelo(usuarioId, item));
	}

	@GetMapping("/lista")
	public ResponseEntity<CollectionModel<EntityModel<Credencial>>> obterCredenciais(
			@PathVariable long usuarioId) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		List<EntityModel<Credencial>> modelos = dono.getCredenciais().stream()
				.map(item -> montarModelo(usuarioId, item)).toList();
		Link self = linkTo(methodOn(UsuarioCredencialControle.class).obterCredenciais(usuarioId)).withSelfRel();
		Link donoLink = linkTo(methodOn(UsuarioControle.class).obterUsuario(usuarioId)).withRel("usuario");
		return ResponseEntity.ok(CollectionModel.of(modelos, self, donoLink));
	}

	@PostMapping("/cadastro")
	public ResponseEntity<EntityModel<Credencial>> cadastrarCredencial(@PathVariable long usuarioId,
			@RequestBody Credencial credencial) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		credencial.setId(null);
		if (credencial.getCriacao() == null) {
			credencial.setCriacao(new Date());
		}
		if (credencial.getInativo() == null) {
			credencial.setInativo(false);
		}
		dono.getCredenciais().add(credencial);
		repositorio.saveAndFlush(dono);
		EntityModel<Credencial> modelo = montarModelo(usuarioId, credencial);
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
	}

	@PutMapping("/atualizar")
	public ResponseEntity<EntityModel<Credencial>> atualizarCredencial(@PathVariable long usuarioId,
			@RequestBody Credencial atualizacao) {
		if (atualizacao.getId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o id da credencial a atualizar");
		}
		Usuario dono = obterDonoOuFalhar(usuarioId);
		Credencial item = buscar(dono, atualizacao.getId());
		if (item == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credencial não encontrada para este usuário");
		}
		if (!atualizador.mesmoTipo(item, atualizacao)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"O tipo de uma credencial existente não pode ser alterado");
		}
		atualizador.atualizar(item, atualizacao);
		repositorio.saveAndFlush(dono);
		return ResponseEntity.ok(montarModelo(usuarioId, item));
	}

	@DeleteMapping("/excluir/{id}")
	public ResponseEntity<Void> excluirCredencial(@PathVariable long usuarioId, @PathVariable long id) {
		Usuario dono = obterDonoOuFalhar(usuarioId);
		boolean removido = dono.getCredenciais().removeIf(item -> item.getId() != null && item.getId() == id);
		if (!removido) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credencial não encontrada para este usuário");
		}
		repositorio.saveAndFlush(dono);
		return ResponseEntity.noContent().build();
	}
}
