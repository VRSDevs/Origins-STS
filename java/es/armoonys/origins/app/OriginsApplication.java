package es.armoonys.origins.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import es.armoonys.origins.chat.ChatService;
import es.armoonys.origins.rooms.ground.GroundMService;
import es.armoonys.origins.rooms.ground.GroundRService;
import es.armoonys.origins.users.UserService;

@SpringBootApplication
@EnableWebSocket
public class OriginsApplication implements WebSocketConfigurer{
	//******************* Variables servicios ************************//
	ChatService chatSrv = new ChatService();
	UserService userSrv = new UserService();
	GroundRService groundRSrv = new GroundRService();
	GroundMService groundMSrv = new GroundMService(groundRSrv);

	//******************* Método principal ************************//
	public static void main(String[] args) {
		SpringApplication.run(OriginsApplication.class, args);
	}
	
	//******************* Métodos para WS ************************//
	/**
	 * Método para insertar el servicio en cuestión a un manejador del registro de WS
	 */
	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry reg) {
		reg.addHandler(createChatService(), "/chat")
			.setAllowedOrigins("*");
		reg.addHandler(createUserService(), "/user")
			.setAllowedOrigins("*");
		reg.addHandler(createGroundRoom(), "/groundR")
			.setAllowedOrigins("*");
		reg.addHandler(createGroundMatch(), "/groundM")
			.setAllowedOrigins("*");
	}
	
	// Creación servicios //
	/**
	 * Creación del servicio del chat
	 * @return
	 */
	@Bean
	public ChatService createChatService() {
		return chatSrv;
	}
	
	/**
	 * Creación del servicio de usuarios
	 * @return
	 */
	@Bean
	public UserService createUserService() {
		return userSrv;
	}
	
	/**
	 * Creación del servicio de la sala de espera de tierra
	 * @return
	 */
	@Bean
	public GroundRService createGroundRoom() {
		return groundRSrv;
	}
	
	/**
	 * Creación del servicio de la partida de tierra
	 * @return
	 */
	@Bean
	public GroundMService createGroundMatch() {
		return groundMSrv;
	}

}
