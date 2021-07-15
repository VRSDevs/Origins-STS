package es.armoonys.origins.chat;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChatService extends TextWebSocketHandler{
	//******************* Variables genéricas ************************//
	// Sesiones del socket
	private Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();	
	// Objeto para mapear los nodos JSON recibidos
	private ObjectMapper mapper = new ObjectMapper();
	// Plantilla de la BD
	@Autowired
	private JdbcTemplate templateOriginsDB;
	// ID próximo mensaje
	AtomicLong nextId;												
	
	//******************* Métodos ************************//
	// Métodos sobrecargados //
	/**
	 * Método invocado cuando un cliente establece conexión con el socket
	 * @param session -> Sesión del cliente que abrió la conexión
	 * @throws IOException
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		sessions.put(session.getId(), session);
	}
	
	/**
	 * Método ejecutado tras cerrar una conexión al socket
	 * @param session -> Sesión del cliente que cerró la conexión
	 * @param status -> Estado de cierre
	 * @throws IOException
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		sessions.remove(session.getId());
	}
	
	/**
	 * Método para gestionar los mensajes recibidos por parte de los clientes
	 * @param session -> Sesión del cliente que mandó el mensaje al servidor
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
			// Caso: OK_GETMESSAGES -> Envío al usuario de todos los mensajes de la BD
			case "OK_GETMESSAGES":
				// Método de obtención de mensajes de la BD
				getMessagesFromDB(session, node);
				break;
			// Caso: OK_SENDMESSAGE -> Un usuario concreto quiere mandar información al resto de clientes
			case "OK_SENDMESSAGE":
				System.out.println("[SERVER] Mensaje recibido de " + node.get("name").asText());
				
				// Envío al resto de jugadores el mensaje del usuario
				sendOtherUsers(session, node);
				break;
		}
	}

	// Métodos de obtención de información //
	/**
	 * Método de obtención de todos los mensajes de la BD
	 * @param session -> Sesión del cliente que desea recibir los mensajes
	 * @param node -> Nodo de información con el mensaje enviado por parte del cliente
	 * @throws IOException
	 */
	private void getMessagesFromDB(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de todos los mensajes de la base de datos
        List<ObjectNode> listOfMessages = templateOriginsDB.query("SELECT * FROM messages", new RowMapper<ObjectNode>() {
            // Obtención del contenido de fila del mapa (en la base de datos, cada fila es un mensaje)
        	@Override
            public ObjectNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        		// Generación e inserción de la información en el objeto para enviar
        		ObjectNode newNode = mapper.createObjectNode();
        		newNode.put("code", node.get("code").asText());
        		newNode.put("name", rs.getString("Username"));
        		newNode.put("message", rs.getString("Body"));
        		
        		return newNode;
            }

        });
        
        // Asignación de la nueva ID para los mensajes
        nextId = new AtomicLong(listOfMessages.size());
        
        // Envío de cada uno de los mensajes al cliente
        for(ObjectNode message : listOfMessages) {
        	session.sendMessage(new TextMessage(message.toString()));
		}
	}
	
	// Métodos de notificación //
	/**
	 * Método para notificar a todos los clientes el mensaje enviado por otro cliente
	 * @param session -> Sesión actual del jugador
	 * @param node -> Nodo de información a mapear para mandarlo a los usuarios
	 * @throws IOException
	 */
	private void sendOtherUsers(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de valores
		long id = nextId.incrementAndGet();
		String name = node.get("name").asText();
		String message = node.get("message").asText();
		
		// Inserción en la BD
		templateOriginsDB.update("INSERT INTO messages(ID,Username,Body) VALUES('" + id +"','"+ name +"','"+ message +"')");
				
		// Generación del mensaje a enviar a un cliente específico
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", node.get("code").asText());
		newNode.put("name", name);
		newNode.put("message", message);
		
		// Obtención de cada una de las sesiones en el socket
		for(WebSocketSession participant : sessions.values()) {
			// Si no es el mismo que mandó el mensaje
			if(!participant.getId().equals(session.getId())) {
				// Envío del mensaje
				participant.sendMessage(new TextMessage(newNode.toString()));
			}
		}
	}
}
