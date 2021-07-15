package es.armoonys.origins.users;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UserService extends TextWebSocketHandler{
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
	//******************* Variables específicas ************************//
	// Número de usuarios conectados al socket
	private int connectedUsers = 0;
	// Mapa de información de jugadores conectados
	private Map<String, ObjectNode> userInfos = new ConcurrentHashMap<>();	
	
	//******************* Métodos ************************//
	// Métodos sobrecargados //
	/**
	 * Método invocado cuando un cliente establece conexión con el socket
	 * @param session -> Sesión del cliente que abrió la conexión
	 * @throws IOException
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		// Actualización del número de usuarios e inserción del usuario al mapa de sesiones
		connectedUsers++;
		sessions.put(session.getId(), session);
		
		// Muestra de información y comunicación al resto de clientes
		System.out.println("[SERVER] Usuarios conectados al servidor: " + connectedUsers);		
		notifyConnectedUsers();
	}
	
	/**
	 * Método ejecutado tras cerrar una conexión al socket
	 * @param session -> Sesión del cliente que cerró la conexión
	 * @param status -> Estado de cierre
	 * @throws IOException
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		// Actualización del número de usuarios y eliminación del usuario del mapa de sesiones
		connectedUsers--;
		
		removeUserInfo(session);
		
		// Muestra de información y comunicación al resto de clientes
		System.out.println("[SERVER] Usuarios conectados al servidor: " + connectedUsers);
		notifyConnectedUsers();
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
			// Caso: OK_CHECKREGISTER -> Comprobación de la existencia del usuario en la BD
			case "OK_CHECKREGISTER":
				// Método de comprobación de registro
				checkRegister(session, node);
				break;
			// Caso: OK_CHECKLOG -> Comprobación de la existencia del usuario en la BD
			case "OK_CHECKLOG":
				// Método de comprobación de inicio de sesión
				checkLogIn(session, node);
				break;
			// Caso: OK_CONNECTEDNEWUSER -> Envío a la BD y al resto de usuarios el nuevo usuario conectado
			case "OK_CONNECTEDNEWUSER":
				// Método de obtención de usuarios conectados de la BD
				notifyNewUserConnected(session, node);
				break;
			// Caso: OK_CONNECTEDNEWUSER -> Envío a la BD y al resto de usuarios el nuevo usuario conectado
			case "OK_CONNECTEDUSER":
				// Método de obtención de usuarios conectados de la BD
				notifyUserConnect(session, node);
				break;
			// Caso: OK_GETLISTUSERS -> Envío al usuario de todos los usuarios conectados de la BD
			case "OK_GETLISTUSERS":
				getConnectedUsersFromDB(session, node);
				break;
			// Caso: OK_GETLISTUSERS -> Envío al usuario de la desconexión de un usuario en específico
			case "OK_SENDUSERDISCONNECTION":
				notifyUserDisconnect(session, node);
				break;
		}	
	}

	// Métodos de obtención de información //
	/**
	 * Método para obtener todos los usuarios de la BD
	 * @param session
	 * @param node
	 * @throws IOException
	 */
	private void getConnectedUsersFromDB(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de todos los mensajes de la base de datos
        List<ObjectNode> listOfConnectedUsers = templateOriginsDB.query("SELECT * FROM users", new RowMapper<ObjectNode>() {
            // Obtención del contenido de fila del mapa (en la base de datos, cada fila es un mensaje)
        	@Override
            public ObjectNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        		// Generación e inserción de la información en el objeto para enviar
        		ObjectNode newNode = mapper.createObjectNode();
        		newNode.put("code", node.get("code").asText());
        		newNode.put("username", rs.getString("Username"));
        		newNode.put("status", rs.getBoolean("Status"));
        		
        		return newNode;
            }

        });
        
        // Asignación de la nueva ID para los mensajes
        nextId = new AtomicLong(listOfConnectedUsers.size());
	}
	
	/**
	 * Método para comprobar si el usuario puede completar el registro
	 * @param session
	 * @param node
	 * @throws IOException
	 */
	private void checkRegister(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de datos del nodo de mensaje
		String username = node.get("username").asText();
				
		// Obtención de todos los mensajes de la base de datos
        List<ObjectNode> userFromBD = templateOriginsDB.query("SELECT * FROM users WHERE Username = '" + username + "'", new RowMapper<ObjectNode>() {
            // Obtención del contenido de fila del mapa (en la base de datos, cada fila es un mensaje)
        	@Override
            public ObjectNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        		// Generación e inserción de la información en el objeto para enviar
        		ObjectNode newNode = mapper.createObjectNode();
        		newNode.put("code", node.get("code").asText());
        		newNode.put("username", node.get("username").asText());
        		
        		return newNode;
            }

        });
        
        // Generación e inserción de la información en el objeto para enviar
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", node.get("code").asText());
        
        // Si no se encontró el usuario proporcionado
        if(userFromBD.size() == 0) {
        	// Se puede completar el registro
        	newNode.put("status", 2);
        	System.out.println("[SERVER] Se registró el usuario " + username);
        } else {
        	// No se puede completar el registro
        	newNode.put("status", 0);
        }
        
        session.sendMessage(new TextMessage(newNode.toString()));
	}
	
	/**
	 * Método para comprobar si el usuario puede completar el inicio de sesión
	 * @param session
	 * @param node
	 * @throws IOException
	 */
	private void checkLogIn(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de datos del nodo de mensaje
		String username = node.get("username").asText();
		String password = node.get("password").asText();
				
		// Obtención de todos los mensajes de la base de datos
        List<ObjectNode> userFromBD = templateOriginsDB.query("SELECT * FROM users WHERE Username = '" + username + "'", new RowMapper<ObjectNode>() {
            // Obtención del contenido de fila del mapa (en la base de datos, cada fila es un mensaje)
        	@Override
            public ObjectNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        		// Generación e inserción de la información en el objeto para enviar
        		if(rs.getString("Password").equals(password)) {
        			ObjectNode newNode = mapper.createObjectNode();
            		newNode.put("code", node.get("code").asText());
            		newNode.put("username", node.get("username").asText());
            		newNode.put("status", rs.getBoolean("Status"));
            		
            		System.out.println("[SERVER] Usuario encontrado.");
            		
            		return newNode;
        		} else {
        			return null;
        		}        		
            }
        });
        
        // Generación e inserción de la información en el objeto para enviar
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", node.get("code").asText());
        
        // Si no se encontró el usuario proporcionado
        if(userFromBD.size() != 0) {
        	if(!userFromBD.get(0).get("status").asBoolean()) {
        		// Se puede completar el inicio de sesión
            	newNode.put("status", 2);
            	System.out.println("[SERVER] El usuario " + username + " inició sesión");
        	} else {
        		// Se puede completar el inicio de sesión
            	newNode.put("status", 1);
        	}
        } else {
        	// No se puede completar el inicio de sesión
        	newNode.put("status", 0);
        }
        
        // Envío del acceso al cliente
        session.sendMessage(new TextMessage(newNode.toString()));
	}
	
	// Métodos de actualización //
	/**
	 * Método para eliminar la información del usuario en el servidor
	 * @param session -> Sesión del cliente cerrado
	 */
	private void removeUserInfo(WebSocketSession session) {
		// Obtención de ID
		String id = session.getId();
		
		if(userInfos.get(id) == null) {
			return;
		}
		
		// Obtención de variables
		String username = userInfos.get(id).get("username").asText();
		String password = userInfos.get(id).get("password").asText();
		boolean status = false;
		
		System.out.println("[SERVER] El usuario " + username + " cerró la conexión");
		
		// Envío del usuario a la BD
		templateOriginsDB.update("UPDATE users SET Username = '" + username + "', Password = '" + 
				password + "', Status = '" + status + "' WHERE Username = '" + username + "'");
			
		// Eliminación de mapas
		sessions.remove(id);
		userInfos.remove(id);
	}
	
	// Métodos de notificación //
	/**
	 * Método para enviar el número de usuarios a todos los clientes
	 * @throws IOException
	 */
	private void notifyConnectedUsers() throws IOException {
		// Generación e inserción de la información en el objeto para enviar
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", "OK_ALLUSERSCONNECTED");
		newNode.put("connectedUsers", connectedUsers);
		
		// Envío del objeto de información a cada uno de los participantes en la sesión
		for(WebSocketSession participant : sessions.values()) {
			// Si no es el mismo que mandó el mensaje
			if(participant.isOpen()) {
				// Envío de un mensaje con la información del jugador
				participant.sendMessage(new TextMessage(newNode.toString()));
			}
		}
	}
	
	/**
	 * Método para insertar al usuario en la BD y notificación al resto de usuarios
	 * @param session
	 * @param node
	 * @throws IOException
	 */
	private void notifyNewUserConnected(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de la ID
		long id = nextId.incrementAndGet();
		
		// Obtención de datos del nodo de mensaje
		String username = node.get("username").asText();
		String password = node.get("password").asText();
		boolean status = node.get("status").asBoolean();
		
		// Envío del usuario a la BD
		templateOriginsDB.update("INSERT INTO users(ID,Username,Password,Status) VALUES('" + 
				id + "','" + username + "','" + password + "','" + status + "')");
		
		// Generación e inserción de la información en el objeto para enviar
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", node.get("code").asText());
		newNode.put("username", username);
		newNode.put("password", password);
		newNode.put("status", status);
		
		// Almacenamiento en mapa de informaciones de usuario
		userInfos.put(session.getId(), newNode);
		
		// Envío del objeto de información a cada uno de los participantes en la sesión
		for(WebSocketSession participant : sessions.values()) {
			participant.sendMessage(new TextMessage(newNode.toString()));
		}
	}
	
	/**
	 * Método para actualizar al usuario en la BD y notificación al resto de usuarios
	 * @param session -> Cliente que envió el mensaje
	 * @param node -> Nodo de información enviado por el cliente
	 * @throws IOException
	 */
	private void notifyUserConnect(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de datos del nodo de mensaje
		String username = node.get("username").asText();
		String password = node.get("password").asText();
		boolean status = node.get("status").asBoolean();
				
		// Obtención de la ID del usuario a desconectar
		List<ObjectNode> userToConnect = templateOriginsDB.query("SELECT * FROM users WHERE Username = '" + username + "'", new RowMapper<ObjectNode>() {
            // Obtención del contenido de fila del mapa (en la base de datos, cada fila es un mensaje)
        	@Override
            public ObjectNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        		// Generación e inserción de la información en el objeto para enviar
        		ObjectNode newNode = mapper.createObjectNode();
        		newNode.put("code", node.get("code").asText());
        		newNode.put("username", rs.getString("Username"));
        		
        		return newNode;
            }

        });
		
		// Generación e inserción de la información en el objeto para enviar
		ObjectNode newNode = mapper.createObjectNode();
		newNode.put("code", node.get("code").asText());
		newNode.put("username", username);
		newNode.put("password", password);
		newNode.put("status", status);
				
		// Almacenamiento en mapa de informaciones de usuario
		userInfos.put(session.getId(), newNode);
		
		// Actualización de la información en la BD
		if(userToConnect.size() > 0) {
			templateOriginsDB.update("UPDATE users SET Username = '" + username + "', Password = '" + 
			password + "', Status = '" + status + "' WHERE Username = '" + username + "'");
		}

		// Obtención de cada una de las sesiones en el socket
		for(WebSocketSession participant : sessions.values()) {
			// Si no es el mismo que mandó el mensaje
			if(!participant.getId().equals(session.getId())) {
				// Envío de un mensaje con la información del jugador
				participant.sendMessage(new TextMessage(userToConnect.get(0).toString()));
			}
		}
	}
	
	/**
	 * Método para actualizar la desconexión del usuario en la BD y notificación al resto de usuarios
	 * @param session -> Cliente que envió el mensaje
	 * @param node -> Nodo de información enviado por el cliente
	 * @throws IOException
	 */
	private void notifyUserDisconnect(WebSocketSession session, JsonNode node) throws IOException {
		// Obtención de datos del nodo de mensaje
		String username = node.get("username").asText();
		String password = node.get("password").asText();
		boolean status = node.get("status").asBoolean();
				
		// Obtención de la ID del usuario a desconectar
		List<ObjectNode> userToDisconnect = templateOriginsDB.query("SELECT * FROM users WHERE Username = '" + username + "'", new RowMapper<ObjectNode>() {
            // Obtención del contenido de fila del mapa (en la base de datos, cada fila es un mensaje)
        	@Override
            public ObjectNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        		// Generación e inserción de la información en el objeto para enviar
        		ObjectNode newNode = mapper.createObjectNode();
        		newNode.put("code", node.get("code").asText());
        		newNode.put("username", rs.getString("Username"));
        		
        		return newNode;
            }

        });

		// Obtención de cada una de las sesiones en el socket
		for(WebSocketSession participant : sessions.values()) {
			// Si no es el mismo que mandó el mensaje
			if(!participant.getId().equals(session.getId())) {
				// Envío de un mensaje con la información del jugador
				participant.sendMessage(new TextMessage(userToDisconnect.get(0).toString()));
			}
		}
	}
}
