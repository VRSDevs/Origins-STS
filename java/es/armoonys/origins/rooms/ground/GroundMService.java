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

public class GroundMService extends TextWebSocketHandler {
	//******************* Variables genéricas ************************//
	// Sesiones del socket
	private Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	// Objeto para mapear los nodos JSON recibidos
	private ObjectMapper mapper = new ObjectMapper();
	//******************* Variables partida ************************//
	private int matterPosX = 0;
	private int matterPosY = 0;
	private int roundTime = 0;
	//******************* Otras variables ************************//
	private int rIdx = 0;
	private GroundRService roomObj;
	private boolean busy = false;
	//******************* Usuarios ************************//
	private int connectedUsers = 0;
	private int finishedUsers = 0;	

	//******************* Constructor ************************//
	public GroundMService(GroundRService grService) {
		// TODO Auto-generated constructor stub
		rIdx = getRandomIndex();
		matterPosX = getMatterPosX();
		matterPosY = getMatterPosY();
		roundTime = 30;
		
		roomObj = grService;
	}
	
	//******************* Métodos ************************//
	// Métodos sobrecargados //
	/**
	 * Método invocado cuando un cliente establece conexión con el socket
	 * @param session -> Sesión del cliente
	 * @throws IOException
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		// Inserción en el mapa de sesiones y actualización de usuarios conectados
		sessions.put(session.getId(), session);	
		connectedUsers++;
		
		// Notificación de estado inicial de partida
		notifyInitialState(session);
	}
	
	/**
	 * Método ejecutado tras cerrar una conexión al socket
	 * @param session -> Sesión del cliente
	 * @param status -> Estado de cierre
	 * @throws IOException
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		// Actualización valor de variable controladora de partida
		roomObj.matchStarted = false;
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
		switch(codeMessage) {
			// Caso: OK_PLAYERINFO -> Caso para notificar la actualización de la información del usuario
			case "OK_PLAYERINFO":
				notifyPlayerUpdate(session, node);
				break;
			// Caso: OK_POINTSINFO -> Caso para notificar la actualización de la puntuación del usuario
			case "OK_POINTSINFO":
				notifyPointsUpdate(session, node);
				break;
			// Caso: OK_TAKEDM -> Caso para notificar la actualización de la materia oscura
			case "OK_TAKEDM":
				notifyDarkMTaken(session, node);
				break;
			// Caso: OK_ROUNDSTATE -> Caso para notificar el cambio de ronda
			case "OK_ROUNDSTATE":
				// Actualización de usuarios finalizados
				finishedUsers++;
				
				// Si el número es igual al de conectados
				if(finishedUsers == connectedUsers){
					// Notificación del estado nuevo de ronda
					notifyRoundState();
					// Reinicio de variable
					finishedUsers = 0;
				}
				break;
		}
	}
	
	// Métodos notificación //
	/**
	 * Método para notificar el estado inicial de la partida
	 * @param session
	 * @throws IOException
	 */
	private void notifyInitialState(WebSocketSession session) throws IOException {
		// Generación del mensaje a enviar a un cliente específico
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_INITIALSTATE");
		newNode.put("matterX", matterPosX);
		newNode.put("matterY", matterPosY);
		newNode.put("roundTime", roundTime);
		
		// Envío de la información al cliente específico
		session.sendMessage(new TextMessage(newNode.toString()));
	}
	
	/**
	 * Método para notificar el estado nuevo de ronda
	 * @param session
	 * @throws IOException
	 */
	private void notifyRoundState() throws IOException {
		// Reinicio de variables
		rIdx = getRandomIndex();
		matterPosX = getMatterPosX();
		matterPosY = getMatterPosY();
		roundTime = 30;
		
		// Generación del mensaje a enviar a un cliente específico
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_ROUNDSTATE");
		newNode.put("matterX", matterPosX);
		newNode.put("matterY", matterPosY);
		newNode.put("roundTime", roundTime);
		
		// Obtención de cada una de las sesiones en el socket
		for(WebSocketSession participant : sessions.values()) {
			// Envío de un mensaje con la información del jugador
			participant.sendMessage(new TextMessage(newNode.toString()));
		}
	}
	
	/**
	 * Método para notificar la nueva información del usuario que mandó el mensaje
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void notifyPlayerUpdate(WebSocketSession session, JsonNode node) throws IOException {
		// Generación del mensaje a enviar al resto de clientes
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_PLAYERINFO");								// Código del mensaje
		newNode.put("userId", node.get("userID").asInt());					// ID a asignar al cliente conectado
		newNode.put("userVictim", node.get("userVictim").asInt());
		newNode.put("updateKey", node.get("updateKey").asText());			// Tipo del jugador (elemento)
		
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
	 * Método para notificar la nueva puntuación del usuario que mandó el mensaje
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void notifyPointsUpdate(WebSocketSession session, JsonNode node) throws IOException {
		// Generación del mensaje a enviar al resto de clientes
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_POINTSINFO");										// Código del mensaje
		newNode.put("userId", node.get("userID").asInt());							// ID a asignar al cliente conectado
		newNode.put("updatedPoints", node.get("updatedPoints").asInt());			// Tipo del jugador (elemento)
		
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
	 * Método para notificar la actualización de la materia oscura
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void notifyDarkMTaken(WebSocketSession session, JsonNode node) throws IOException {
        // Generación del mensaje a enviar al resto de clientes
        ObjectNode newNode = mapper.createObjectNode();
        newNode.put("code", "OK_TAKEDM");                                // Código del mensaje
        newNode.put("userTaken", node.get("userTaken").asInt());            // ID a asignar al cliente conectado

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
	 * Método para notificar el final de ronda
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @param winnerID -> ID del ganador de la ronda
	 * @throws IOException
	 */
	public void notifyEndRound(WebSocketSession session, JsonNode node, int winnerID) throws IOException {
		// Generación del mensaje a enviar al resto de clientes
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_ENDROUNDINFO");
		newNode.put("winnerUser", winnerID);
		
		// Envío de la información al cliente
		session.sendMessage(new TextMessage(newNode.toString()));
	}
	

	/**
	 * Método para notificar la actualización de tiempo a los usuarios
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void notifyTimeUpdate() throws IOException {
        while(busy);
        
        busy = true;
		
		// Generación del mensaje a enviar al resto de clientes
        ObjectNode newNode = mapper.createObjectNode();
        newNode.put("code", "OK_TIMER");
        newNode.put("timer", roundTime);

        for(WebSocketSession participant : sessions.values()) {
        	// Envío de un mensaje con la información del jugador
            participant.sendMessage(new TextMessage(newNode.toString()));
        }

        busy = false;
    }
	
	
	
	// Otros métodos //
	/**
	 * Método para calcular índice aleatorio empleado para obtener la posición de la materia oscura
	 * @return Índice aleatorio para la posición de la materia oscura
	 */
	private int getRandomIndex() {
		// Inicialización y valor del índice aleatorio
		int i = (int) Math.floor(Math.random() * 4);
		
		// Devolución del índice
		return i;
	}
	
	/**
	 * Método para obtener la posX de la materia oscura
	 * @return Posición X de la materia oscura
	 */
	private int getMatterPosX() {
		// Inicialización de valores en X
		int[] allX = {200, 400, 530, 400};
		
		// Devolución de valor
		return allX[rIdx];
	}
	
	/**
	 * Método para obtener la posY de la materia oscura
	 * @return Posición Y de la materia oscura
	 */
	private int getMatterPosY() {
		// Inicialización de valores en Y
		int[] allY = {500, 120, 460, 530};
		
		// Devolución de valor
		return allY[rIdx];
	}
}