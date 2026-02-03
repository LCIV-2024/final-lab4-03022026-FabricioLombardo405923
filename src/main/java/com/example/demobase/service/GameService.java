package com.example.demobase.service;

import com.example.demobase.dto.GameDTO;
import com.example.demobase.dto.GameResponseDTO;
import com.example.demobase.model.Game;
import com.example.demobase.model.GameInProgress;
import com.example.demobase.model.Player;
import com.example.demobase.model.Word;
import com.example.demobase.repository.GameInProgressRepository;
import com.example.demobase.repository.GameRepository;
import com.example.demobase.repository.PlayerRepository;
import com.example.demobase.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    
    private final GameRepository gameRepository;
    private final GameInProgressRepository gameInProgressRepository;
    private final PlayerRepository playerRepository;
    private final WordRepository wordRepository;
    
    private static final int MAX_INTENTOS = 7;
    private static final int PUNTOS_PALABRA_COMPLETA = 20;
    private static final int PUNTOS_POR_LETRA = 1;
    
    @Transactional
    public GameResponseDTO startGame(Long playerId) {
        // TODO: Implementar el método startGame
        GameResponseDTO response = new GameResponseDTO();

        // Validar que el jugador existe
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new RuntimeException("Jugador no encontrado"));

        // Buscar una palabra no utilizada aleatoriamente
        Word palabraSeleccionada = wordRepository.findRandomWord()
                .orElseThrow(() -> new RuntimeException("No hay palabras disponibles"));

        // Verificar si ya existe una partida en curso para este jugador y palabra
        Optional<GameInProgress> partidaExistente = gameInProgressRepository
                .findByJugadorAndPalabra(playerId, palabraSeleccionada.getId());

        if (partidaExistente.isPresent()) {
            // Si ya existe, retornar el estado actual
            return buildResponseFromGameInProgress(partidaExistente.get());
        }

        // Marcar la palabra como utilizada
        palabraSeleccionada.setUtilizada(true);
        wordRepository.save(palabraSeleccionada);

        // Crear nueva partida en curso
        GameInProgress nuevaPartida = new GameInProgress();
        nuevaPartida.setJugador(player);
        nuevaPartida.setPalabra(palabraSeleccionada);
        nuevaPartida.setIntentosRestantes(MAX_INTENTOS);
        nuevaPartida.setLetrasIntentadas("");
        nuevaPartida.setFechaInicio(LocalDateTime.now());

        gameInProgressRepository.save(nuevaPartida);

        // Construir respuesta inicial
        response.setPalabraOculta(generateHiddenWord(palabraSeleccionada.getPalabra().toUpperCase(), new HashSet<>()));
        response.setLetrasIntentadas(new ArrayList<>());
        response.setIntentosRestantes(MAX_INTENTOS);
        response.setPalabraCompleta(false);
        response.setPuntajeAcumulado(0);

        return response;
    }
    
    @Transactional
    public GameResponseDTO makeGuess(Long playerId, Character letra) {
        GameResponseDTO response = new GameResponseDTO();
        // TODO: Implementar el método makeGuess
        // Validar que el jugador existe
        Optional<Player> playerOptional = playerRepository.findById(playerId);
        if (playerOptional.isEmpty()) {
            throw new RuntimeException("Jugador no encontrado");
        }
        Player player = playerOptional.get();
        // Convertir la letra a mayúscula
        letra = Character.toUpperCase(letra);
        // Buscar la partida en curso más reciente del jugador
        List<GameInProgress> partidasEnCurso = gameInProgressRepository.findByJugadorIdOrderByFechaInicioDesc(player.getId());
        if (partidasEnCurso.isEmpty()) {
            throw new RuntimeException("No existe una partida en curso para este jugador");
        }
        // Tomar la partida más reciente
        GameInProgress gameInProgress = partidasEnCurso.get(0);
        // Obtener letras ya intentadas
        Set<Character> letrasIntentadas = stringToCharSet(gameInProgress.getLetrasIntentadas());
        // Verificar si la letra ya fue intentada
        if (letrasIntentadas.contains(letra)) {
            return buildResponseFromGameInProgress(gameInProgress);
        }
        // Agregar la nueva letra
        letrasIntentadas.add(letra);
        gameInProgress.setLetrasIntentadas(charSetToString(letrasIntentadas));
        // Verificar si la letra está en la palabra
        boolean letraCorrecta = gameInProgress.getPalabra().getPalabra().indexOf(letra) >= 0;
        // Decrementar intentos solo si la letra es incorrecta
        if (!letraCorrecta) {
            gameInProgress.setIntentosRestantes(gameInProgress.getIntentosRestantes() - 1);
        }
        // Generar palabra oculta
        String palabraOculta = generateHiddenWord(gameInProgress.getPalabra().getPalabra(), letrasIntentadas);
        boolean palabraCompleta = palabraOculta.equals(gameInProgress.getPalabra().getPalabra());
        // Guardar el estado actualizado
        gameInProgressRepository.save(gameInProgress);
        // Si el juego terminó, guardar en Game y eliminar de GameInProgress
        boolean juegoTerminado = gameInProgress.getIntentosRestantes() == 0 || palabraCompleta;
        if (juegoTerminado) {
            boolean ganado = palabraCompleta;
            int puntaje = calculateScore(gameInProgress.getPalabra().getPalabra(), letrasIntentadas, palabraCompleta, gameInProgress.getIntentosRestantes());
            saveGame(player, gameInProgress.getPalabra(), ganado, puntaje);
            gameInProgressRepository.delete(gameInProgress);
        }
        // Construir respuesta
        response.setPalabraOculta(palabraOculta);
        response.setLetrasIntentadas(new ArrayList<>(letrasIntentadas));
        response.setIntentosRestantes(gameInProgress.getIntentosRestantes());
        response.setPalabraCompleta(palabraCompleta);
        response.setPuntajeAcumulado(calculateScore(gameInProgress.getPalabra().getPalabra(), letrasIntentadas, palabraCompleta, gameInProgress.getIntentosRestantes()));

        return response;
    }
    
    private GameResponseDTO buildResponseFromGameInProgress(GameInProgress gameInProgress) {
        String palabra = gameInProgress.getPalabra().getPalabra().toUpperCase();
        Set<Character> letrasIntentadas = stringToCharSet(gameInProgress.getLetrasIntentadas());
        String palabraOculta = generateHiddenWord(palabra, letrasIntentadas);
        boolean palabraCompleta = palabraOculta.equals(palabra);
        
        GameResponseDTO response = new GameResponseDTO();
        response.setPalabraOculta(palabraOculta);
        response.setLetrasIntentadas(new ArrayList<>(letrasIntentadas));
        response.setIntentosRestantes(gameInProgress.getIntentosRestantes());
        response.setPalabraCompleta(palabraCompleta);
        
        int puntaje = calculateScore(palabra, letrasIntentadas, palabraCompleta, gameInProgress.getIntentosRestantes());
        response.setPuntajeAcumulado(puntaje);
        
        return response;
    }
    
    private Set<Character> stringToCharSet(String str) {
        Set<Character> set = new HashSet<>();
        if (str != null && !str.isEmpty()) {
            String[] chars = str.split(",");
            for (String c : chars) {
                if (!c.trim().isEmpty()) {
                    set.add(c.trim().charAt(0));
                }
            }
        }
        return set;
    }
    
    private String charSetToString(Set<Character> set) {
        if (set == null || set.isEmpty()) {
            return "";
        }
        return set.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
    
    private int calculateScore(String palabra, Set<Character> letrasIntentadas, boolean palabraCompleta, int intentosRestantes) {
        if (palabraCompleta) {
            return PUNTOS_PALABRA_COMPLETA;
        } else if (intentosRestantes == 0) {
            // Contar letras correctas encontradas
            long letrasCorrectas = letrasIntentadas.stream()
                    .filter(letra -> palabra.indexOf(letra) >= 0)
                    .count();
            return (int) (letrasCorrectas * PUNTOS_POR_LETRA);
        }
        return 0;
    }
    
    private String generateHiddenWord(String palabra, Set<Character> letrasIntentadas) {
        StringBuilder hidden = new StringBuilder();
        for (char c : palabra.toCharArray()) {
            if (letrasIntentadas.contains(c) || c == ' ') {
                hidden.append(c);
            } else {
                hidden.append('_');
            }
        }
        return hidden.toString();
    }
    
    @Transactional
    public void saveGame(Player player, Word word, boolean ganado, int puntaje) {
        // Asegurar que la palabra esté marcada como utilizada
        if (!word.getUtilizada()) {
            word.setUtilizada(true);
            wordRepository.save(word);
        }
        
        Game game = new Game();
        game.setJugador(player);
        game.setPalabra(word);
        game.setResultado(ganado ? "GANADO" : "PERDIDO");
        game.setPuntaje(puntaje);
        game.setFechaPartida(LocalDateTime.now());
        gameRepository.save(game);
    }
    
    public List<GameDTO> getGamesByPlayer(Long playerId) {
        return gameRepository.findByJugadorId(playerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<GameDTO> getAllGames() {
        return gameRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    private GameDTO toDTO(Game game) {
        GameDTO dto = new GameDTO();
        dto.setId(game.getId());
        dto.setIdJugador(game.getJugador().getId());
        dto.setNombreJugador(game.getJugador().getNombre());
        dto.setResultado(game.getResultado());
        dto.setPuntaje(game.getPuntaje());
        dto.setFechaPartida(game.getFechaPartida());
        dto.setPalabra(game.getPalabra() != null ? game.getPalabra().getPalabra() : null);
        return dto;
    }
}

