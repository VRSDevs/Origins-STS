package es.armoonys.origins.rooms.ground;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class GroundRService extends TextWebSocketHandler{

	//******************* Variables genéricas ************************//
	// Sesiones del socket
	private Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	// Objeto para el envío de mensajes
	private ObjectMapper mapper = new ObjectMapper();
	
	//******************* Variables específicas ************************//
	// Usuarios //
	private int connectedUsers = 0;						// Usuarios conectados a la sala
	private int finishedUsers = 0;						// Usuarios que acabaron la partida
	private final int MAX_USERS = 4;					// Número máximo de usuarios permitidos en la sala
	private int readyPlayers = 0;						// Jugadores listos en el lobby
	private int[] ids = {0, 1, 2, 3};					// IDs asignables a los clientes (jugadores)
	private String[] assignedIds = {"", "", "", ""};	// IDs asignados a clientes
	private Map<String, ObjectNode>
		playerInfos = new ConcurrentHashMap<>();	// Mapa de información de jugadores conectados
	// Partida //
	public boolean matchStarted = false;	// ¿La partida ha comenzado?
	
	//******************* Métodos ************************//
	// Métodos sobrecargados //
	/**
	 * Método invocado cuando un cliente establece conexión con el socket
	 * @param session -> Sesión del cliente
	 * @throws IOException
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		// Si el número de usuarios excede el máximo permitido o la partida ha comenzado
		if (connectedUsers + 1 > MAX_USERS || matchStarted) {
			notifyAndCloseConnection(session);
			return;
		}
		
		// Actualización del número de usuarios
		connectedUsers++;
		// Inserción de la sesión en el mapa de sesiones
		sessions.put(session.getId(), session);
		// Notificación de acceso a la conexión
		notifyAccess(session);
		
		// Muestra de información y comunicación al resto de clientes
		System.out.println("Usuarios conectados a la sala: " + connectedUsers);		
	}
	
	/**
	 * Método ejecutado tras cerrar una conexión al socket
	 * @param session -> Sesión del cliente
	 * @param status -> Estado de cierre
	 * @throws IOException
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		// Comprobación si el jugador estaba listo para jugar o no
		if(playerInfos.get(session.getId()) != null) {
			// Actualización del número de usuarios, jugadores listos y eliminación del usuario del mapa de sesiones
			connectedUsers--;
			
			boolean wasReady = playerInfos.get(session.getId()).get("playerReady").asBoolean();
			if(wasReady)
				// Actualización del número de jugadores (si estaba listo)
				readyPlayers--;
			
			// Notificación al resto de usuarios de que el actual ha abandonado la partida
			notifyRemovePlayer(session);
			
			// Eliminación de la información del jugador en el mapa de jugadores
			removePlayerInfo(session);
			
			// Muestra de información y comunicación al resto de clientes
			System.out.println("[SERVER] Usuarios conectados a la sala de tierra: " + connectedUsers);
			System.out.println("[SERVER] Usuarios listos para jugar: " + readyPlayers);
		}
	}
	
	/**
	 * Método para gestionar los mensajes recibidos por parte de los clientes
	 * @param session -> Sesión del cliente
	 * @param message -> Mensaje recibido del cliente
	 * @throws IOException
	 */
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		// Creación de nodo de JSON del mensaje
		JsonNode node = mapper.readTree(message.getPayload());
		
		// Obtención del código del mensaje
		String codeMessage = node.get("code").asText();
		
		// Ejecución de código en función del código obtenido
		switch (codeMessage) {
			// Caso: OK_PLAYERJOIN -> El usuario ha podido unirse a la sala a la perfección
			case "OK_PLAYERJOIN":
				// Envío de la información de todos los jugadores conectados actualmente
				getPlayerInfos(session);
				// Notificación al resto de jugadores de que un nuevo jugador se unió a la partida
				notifyNewPlayer(session, node);
				break;
			// Caso: OK_PLAYERREADY -> El usuario ha indicado que está listo para empezar la partida
			case "OK_PLAYERREADY":
				notifyPlayerReady(session, node);
				break;
			// Caso: OK_MATCHENDED -> El usuario ha indicado que ha acabado la partida
			case "OK_MATCHENDED":
				// Actualización valor de los jugadores acabados
				finishedUsers++;
				
				// Si el número coincide con los jugadores conectados
				if(finishedUsers == connectedUsers) {
					// Actualización de valor de variable controladora de inicio de partida
					matchStarted = false;
					System.out.println("[SERVER] Partida en sala de tierra finalizada.");
					
					// Limpieza variable contadora
					finishedUsers = 0;
				}
				
				break;
		}
	}
	
	// Métodos de actualización //
	/**
	 * Método para eliminar la información del jugador en el servidor
	 * @param session -> Sesión del cliente cerrado
	 */
	private void removePlayerInfo(WebSocketSession session) {
		// Obtención de la ID
		String id = session.getId();
		
		// Cambio del valor de asignación
		assignedIds[indexOf(id)] = "";
		sessions.remove(id);
		playerInfos.remove(id);
	}
	
	// Métodos de notificación //
	/**
	 * Notifica al cliente que ha podido establecer la conexión con la sala
	 * @param session -> Sesión del cliente
	 * @throws IOException
	 */
	private void notifyAccess(WebSocketSession session) throws IOException {
		// Generación del mensaje a enviar a un cliente específico
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_ROOMCONN");				// Código del mensaje
		newNode.put("userID", getId(session));			// ID a asignar al cliente conectado
						
		// Envío del mensaje
		session.sendMessage(new TextMessage(newNode.toString()));
	}
	
	/**
	 * Método para notificar a todos los jugadores de que un nuevo jugador a entrado al lobby
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void notifyNewPlayer(WebSocketSession session, JsonNode node) throws IOException {
		// Generación del mensaje a enviar a un cliente específico
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_PLAYERJOIN");								// Código del mensaje
		newNode.put("playerId", node.get("playerId").asInt());				// ID a asignar al cliente conectado
		newNode.put("playerType", node.get("playerType").asInt());			// Tipo del jugador (elemento)
		newNode.put("playerName", node.get("playerName").asText());			// Nombre del jugador
		newNode.put("playerReady", node.get("playerReady").asBoolean());	// ¿Está listo del jugador?
		
		// Inserción en mapa de jugadores
		playerInfos.put(session.getId(), newNode);
		
		// Obtención de cada una de las sesiones en el socket
		for(WebSocketSession participant : sessions.values()) {
			// Si no es el mismo que mandó el mensaje
			if(!participant.getId().equals(session.getId())) {
				// Envío de un mensaje con la información del jugador
				participant.sendMessage(new TextMessage(newNode.toString()));
			}
		}	
	}
	
	/**
	 * Método para notificar a todos los jugadores de que uno abandonó la partida
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void notifyRemovePlayer(WebSocketSession session) throws IOException {
		// Generación del mensaje a enviar a un cliente específico
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_PLAYERDISC");								// Código del mensaje
		newNode.put("playerId", ids[indexOf(session.getId())]);				// ID a asignar al cliente conectado
		
		// Obtención de cada una de las sesiones en el socket
		for(WebSocketSession participant : sessions.values()) {
			// Si no es el mismo que mandó el mensaje
			if(!participant.getId().equals(session.getId())) {
				// Envío de un mensaje con la información del jugador
				participant.sendMessage(new TextMessage(newNode.toString()));
			}
		}
	}
	
	/**
	 * Método para notificar a todos los jugadores de que uno indicó que está listo para jugar
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void notifyPlayerReady(WebSocketSession session, JsonNode node) throws IOException {
		// Generación del mensaje a enviar a los clientes
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", node.get("code").asText());
		newNode.put("playerId", node.get("playerId").asInt());
		newNode.put("playerType", node.get("playerType").asInt());			// Tipo del jugador (elemento)
		newNode.put("playerName", node.get("playerName").asText());	
		newNode.put("playerReady", node.get("playerReady").asBoolean());
		
		// Reemplazo de la información en el mapa de jugadores
		playerInfos.remove(session.getId());
		playerInfos.put(session.getId(), newNode);
		System.out.println("Información insertada: " + newNode);

		// Envío del objeto de información a cada uno de los participantes en la sesión
		for(WebSocketSession participant : sessions.values()) {
			// Si no es el mismo que mandó el mensaje
			if(!participant.getId().equals(session.getId())) {
				// Envío de un mensaje con la información de si está listo el jugador
				participant.sendMessage(new TextMessage(newNode.toString()));
			}
		}
		
		// Actualización del valor de jugadores listos
		boolean playerStatus = node.get("playerReady").asBoolean();
		if(playerStatus) {
			readyPlayers++;
		} else {
			readyPlayers--;
		}
		System.out.println("Usuarios listos: " + readyPlayers);
			
		// Comprobación del número de jugadores listos (si es 4 (máximo))
		if(readyPlayers >= 2 && readyPlayers == connectedUsers) {
			// Notificación del inicio de partida
			notifyStartMatch();
		}
	}
	
	/**
	 * Método para notificar a los usuarios de la partida que esta va a comenzar
	 * @throws IOException
	 */
	private void notifyStartMatch() throws IOException {
		// Actualización de variable de control de inicio de partida
		matchStarted = true;
		
		// Generación del mensaje a enviar a todos los clientes
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_STARTMATCH");
		newNode.put("players", connectedUsers);
		
		// Envío del objeto de información a cada uno de los participantes en la sesión
		for(WebSocketSession participant : sessions.values()) {
			participant.sendMessage(new TextMessage(newNode.toString()));
		}
	}
	
	/**
	 * Este método se llama cuando un juegador se intenta conectar y la sala ya se encuentra llena
	 * @param session -> Sesión actual del jugador
	 * @throws IOException
	 */
	private void notifyAndCloseConnection(WebSocketSession session) throws IOException{		
		// Generación del mensaje a enviar a un cliente específico
		ObjectNode newNode = mapper.createObjectNode();
		// Si el número de usuarios es mayor al máximo
		if(connectedUsers + 1 > MAX_USERS) {
			// Código de error por máximo de usuarios
			newNode.put("code", "Error_MAXUSERS");
		} else if (matchStarted) {
			// Código de error por partida empezada
			newNode.put("code", "Error_MATCHSTARTED");
		}
				
		// Envío del mensaje a la sesión
		session.sendMessage(new TextMessage(newNode.toString()));
		// Cierre de la conexión
		session.close();
	}
	
	// Otros //
	/**
	 * Método para obtener una ID del array de ids para el cliente conectado
	 * @return Devuelve el primer id disponible
	 */
	private int getId(WebSocketSession session) {
		// Inicialización del ID
		int newId = -1;
		
		// Búsqueda por el primer ID disponible
		for (int i = 0; i < ids.length; i++) {
			// Si en el array de asignados es 0
			if(assignedIds[i] == "") {
				// Actualización de la ID
				newId = ids[i];
				// Actualización en array de asignados
				assignedIds[i] = session.getId();
				// Rotura del flujo del for
				break;
			}
		}
		
		// Retorno del nuevo ID
		return newId;
	}
	
	/**
	 * Método para buscar el índice de una sesión en el array de índices de partida asignados
	 * @param element -> ID de la sesión a buscar en el array
	 * @return
	 */
	private int indexOf(String element) {
		// Inicialización del índice
		int idx = -1;
		
		// Búsqueda del índice de la sesión en el array de índices de partida asignados
		for (int i = 0; i < assignedIds.length; i++) {
			// Si coincide el ID proporcionado con el del array
			if(element.equalsIgnoreCase(assignedIds[i])) {
				// Asignación al índice auxiliar al encontrado
				idx = i;
				// Rotura del flujo de ejecución del bucle
				break;
			}
		}
		
		// Devolución del índice encontrado
		return idx;
	}
	
	/**
	 * Método para mandar un mensaje por cada jugador en el mapa de jugadores
	 * @param session -> Sesión actual del jugador
	 * @throws IOException
	 */
	private void getPlayerInfos(WebSocketSession session) throws IOException {
		// Para cada objeto de información en el mapa de jugadores
		for(ObjectNode info : playerInfos.values()) {
			// Generación del mensaje a enviar al cliente en cuestión
			ObjectNode infoToSend = mapper.createObjectNode();
			infoToSend.put("code", "OK_GETPLAYERS");
			infoToSend.put("playerId", info.findValue("playerId").asInt());
			infoToSend.put("playerType", info.findValue("playerType").asInt());
			infoToSend.put("playerName", info.findValue("playerName").asText());	
			infoToSend.put("playerReady", info.findValue("playerReady").asBoolean());
			
			// Envío del mensaje
			session.sendMessage(new TextMessage(infoToSend.toString()));
		}
	}
}
