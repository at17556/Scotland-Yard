package uk.ac.bris.cs.scotlandyard.model;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Consumer;

import uk.ac.bris.cs.gamekit.graph.*;

import java.util.HashMap;
import java.util.Map;


import uk.ac.bris.cs.gamekit.graph.Graph;

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements Consumer<Move>, ScotlandYardGame, MoveVisitor, Spectator {
	// Declaring all needed attributes
	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private List<ScotlandYardPlayer> players;
	private Integer playerOrder = 0;
	private Integer roundNumber = NOT_STARTED;
	private int lastKnownLocation = 0;
	private Set<Move> validMoves;
	//	private Set<Colour> winningPlayers;
	private boolean detectivesWin = false;
	private Collection<Spectator> spectators;

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
							 PlayerConfiguration mrX, PlayerConfiguration firstDetective,
							 PlayerConfiguration... restOfTheDetectives) {
		// Initializing attributes
		this.rounds = requireNonNull(rounds);
		this.graph = requireNonNull(graph);
		this.playerOrder = 0;
		this.spectators = new HashSet<Spectator>();

		// Validating attributes
		if (this.rounds.isEmpty()) {
			throw new IllegalArgumentException("Empty Rounds");
		}

		if (this.graph.isEmpty()) {
			throw new IllegalArgumentException("Empty graph");
		}

		if (mrX.colour != BLACK) {
			throw new IllegalArgumentException("MrX should be Black");
		}

		// Creating a list of player configurations
		ArrayList<PlayerConfiguration> configurations = new ArrayList<>();

		for (PlayerConfiguration configuration : restOfTheDetectives) {
			configurations.add(requireNonNull(configuration));
		}

		// Adding the firstDetective at 0th index then add mrX at 0th index, sets the right order
		configurations.add(0, firstDetective);
		configurations.add(0, mrX);

		// Creating a set of player locations and colours - used later to check for duplicate entries
		Set<Integer> locationSet = new HashSet<>();
		Set<Colour> colourSet = new HashSet<>();

		// Checking for duplicate locations and colours
		for (PlayerConfiguration configuration : configurations) {
			if (locationSet.contains(configuration.location)) {
				throw new IllegalArgumentException("Duplicate location");
			}
			if (colourSet.contains(configuration.colour)) {
				throw new IllegalArgumentException(("Duplicate colour"));
			}

			// Populating the sets
			locationSet.add(configuration.location);
			colourSet.add(configuration.colour);

			// Checking that all the players have the required tickets
			if (!configuration.tickets.containsKey(Ticket.BUS)) {
				throw new IllegalArgumentException("Missing tickets");
			}

			if (!configuration.tickets.containsKey(Ticket.TAXI)) {
				throw new IllegalArgumentException("Missing tickets");
			}

			if (!configuration.tickets.containsKey(Ticket.UNDERGROUND)) {
				throw new IllegalArgumentException("Missing tickets");
			}
		}

		// Making sure the first detective don't have secret or double tickets
		if (firstDetective.tickets.get(SECRET) != 0) {
			throw new IllegalArgumentException("Detective has a secret ticket");
		}

		if (firstDetective.tickets.get(DOUBLE) != 0) {
			throw new IllegalArgumentException("Detective has a double ticket");
		}

		// Making sure the rest of the detectives don't have secret or double tickets
		for (PlayerConfiguration detective : restOfTheDetectives) {
			if (detective.tickets.get(SECRET) != 0) {
				throw new IllegalArgumentException("Detective has a secret ticket");
			}
			if (detective.tickets.get(DOUBLE) != 0) {
				throw new IllegalArgumentException("Detective has a double ticket");
			}
		}

		// Creating a new ScotlandYardPlayer ArrayList that will hold all the players of the game
		players = new ArrayList<>();

		// Adding all players to the scotland yard player set with the parameters being the PlayerConfiguration's attributes
		for (PlayerConfiguration pc : configurations) {
			players.add(new ScotlandYardPlayer(pc.player, pc.colour, pc.location, pc.tickets));
		}


	}

	// Start rotate function is used to begin each round, calls the make move function for the first player (Mr X)
	@Override
	public void startRotate() {

		if (isGameOver()) {
			throw new IllegalStateException("Game is over");
		}

		// Fetch the current player and store it inside the currentPlayer object
		ScotlandYardPlayer currentPlayer = players.get(playerOrder);
		int location = currentPlayer.location();

		// Fetch all the valid moves available for the current player
		validMoves = checkValidMoves(playerOrder);

		currentPlayer.player().makeMove(this, location, validMoves, this);

	}

	// Checks the valid moves that a player can make at a certain location by seeing all adjacent nodes and whether
	// or not the player has the appropriate tickets for the destinations + if the destination node is occupied or not
	private Set<Move> checkValidMoves(int player) {

		// Fetch the ScotlandYardPlayer object of the current player
		ScotlandYardPlayer human = players.get(player);

		// Create a new set that will store all of the new possible moves
		Set<Move> moves1 = new HashSet<Move>();

		// Store the current player's location in an integer
		int position = human.location();

		// Store the associated node in a node object
		Node<Integer> currentNode = graph.getNode(position);

		// Retrieve all connected edges from the current node and store it in a collection of edges
		Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(currentNode);

		TicketMove move;

		// Looping through all the edges
		for (Edge<Integer, Transport> edge : edges) {

			boolean valid = true;

			// Obtain the node location that is connected by the edge + method of transport required to travel along the edge
			int destination = edge.destination().value();
			Ticket methodOfTransport = Ticket.fromTransport(edge.data());

			for (ScotlandYardPlayer p : players) {

				// If the destination node is occupied by another detective, set valid to false (It is not a valid move)
				if (p.location() == destination && p.isDetective()) {
					valid = false;
					break;
				}
			}

			if (!valid) {
				continue;
			}

			// If the current player has the ticket required to travel along the edge
			if (human.hasTickets(methodOfTransport)) {

				// Create a new ticket move, passing all the parameters accordingly
				move = new TicketMove(human.colour(), methodOfTransport, destination);

				// Add the created ticket move to the list of valid moves
				moves1.add(move);

				// If the current player has a double ticket available
				if (human.hasTickets(DOUBLE)) {

					// If the current round is not the last round or the second last round (can't execute double moves on last 2 rounds of the game
					if (roundNumber != getRounds().size() - 1 && roundNumber != getRounds().size()) {
						// Execute the check double moves function and add all moves to valid moves set
						moves1.addAll(checkDoubleMoves(edge.destination(), move, player));
					}
				}
			}

			// If the current player has a secret ticket available
			if (human.hasTickets(SECRET)) {
				// Create a new ticket move of type secret
				move = new TicketMove(human.colour(), SECRET, destination);
				moves1.add(move);
				if (human.hasTickets(DOUBLE)) {
					if (roundNumber != getRounds().size() - 1 && roundNumber != getRounds().size()) {
						moves1.addAll(checkDoubleMoves(edge.destination(), move, player));
					}
				}
			}
		}

		// Adds a pass move if a detective has no valid moves
		if (moves1.isEmpty() && human.isDetective()) {
			Move testMove = new PassMove(human.colour());
			moves1.add(testMove);
		}

		// Returns set of valid moves
		return moves1;
	}

	// Checks the valid double moves that mr x could make by going through the adjacent nodes of each of the adjacent nodes
	// Very similar to the function checkValidMoves but the parameter currentNode is the destination node from the first move
	private Set<Move> checkDoubleMoves(Node<Integer> currentNode, TicketMove move, int player) {

		ScotlandYardPlayer human = players.get(player);
		Set<Move> moves2 = new HashSet<Move>();

		Collection<Edge<Integer, Transport>> edges = graph.getEdgesFrom(currentNode);

		TicketMove move2;

		human.removeTicket(move.ticket());

		for (Edge<Integer, Transport> edge : edges) {
			boolean valid = true;
			int destination = edge.destination().value();
			Ticket methodOfTransport = Ticket.fromTransport(edge.data());

			for (ScotlandYardPlayer p : players) {

				if (p.location() == destination && p.isDetective()) {
					valid = false;
					break;
				}
			}

			if (!valid) continue;

			if (human.hasTickets(methodOfTransport)) {
				move2 = new TicketMove(human.colour(), methodOfTransport, destination);
				DoubleMove movesTogether = new DoubleMove(human.colour(), move, move2);
				moves2.add(movesTogether);
			}
			if (human.hasTickets(SECRET)) {
				move2 = new TicketMove(human.colour(), SECRET, destination);
				DoubleMove movesTogether = new DoubleMove(human.colour(), move, move2);
				moves2.add(movesTogether);
			}

		}

		human.addTicket(move.ticket());

		return moves2;

	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableCollection(spectators);
	}

	// Registers a spectator
	@Override
	public void registerSpectator(Spectator spectator) {
		requireNonNull(spectator);
		// Checks if the spectator is already within the spectators collection, if it isn't then add it
		if (!spectators.contains(spectator)) {
			spectators.add(spectator);
		} else {
			throw new IllegalArgumentException("Already registered");
		}
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		requireNonNull(spectator);
		// Checks if the spectator is already within the spectators collection, if it is then remove it
		if (spectators.contains(spectator)) {
			spectators.remove(spectator);
		} else {
			throw new IllegalArgumentException("Already unregistered");
		}
	}

	// Returns a list of the players (colours)
	@Override
	public List<Colour> getPlayers() {
		List<Colour> colours = new ArrayList<>();
		for (ScotlandYardPlayer player : players) {
			colours.add(player.colour());
		}
		return Collections.unmodifiableList(colours);
	}

	// Returns a list of winning players (colours)
	@Override
	public Set<Colour> getWinningPlayers() {
		Set<Colour> winningPlayers = new HashSet<Colour>();
		winningPlayers = addWinningPlayers(winningPlayers);
		return Collections.unmodifiableSet(winningPlayers);
	}

	// Adds the winning players to the list of winning players
	private Set<Colour> addWinningPlayers(Set<Colour> winningPlayers) {

		if (isGameOver()) {
			// detectivesWin is a boolean attribute that is updated in isGameOver()
			// If detectivesWin is true, add all detectives to the list of winning players
			if (detectivesWin) {
				for (ScotlandYardPlayer p : players) {
					if (p.isDetective()) {
						winningPlayers.add(p.colour());
					}
				}
			} else {
				// If detectivesWin is false, add Mr. X to the list of winning players
				winningPlayers.add(BLACK);
			}
		}
		return winningPlayers;
	}

	// Returns the location of a player
	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {

		Optional<Integer> location = Optional.empty();
		if(roundNumber < getRounds().size()) {
			if ((colour == BLACK) && (getCurrentRound() == NOT_STARTED)) {
				location = Optional.of(0);
			} else if ((colour == BLACK)) {
				// Return last known location of Mr X
				return Optional.of(lastKnownLocation);
			} else {
				for (ScotlandYardPlayer player : players) {
					if (player.colour() == colour) {
						return Optional.of(player.location());
					}
				}
			}
		}
		return location;
	}

	// Returns the number of a certain ticket that a player has
	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {

		for (ScotlandYardPlayer player : players) {
			if (player.colour() == colour) {
				Map<Ticket, Integer> ticketMap = player.tickets();
				Integer numTickets = ticketMap.get(ticket);
				Optional<Integer> a = Optional.ofNullable(numTickets);
				return a;
			}
		}
		return Optional.empty();
	}

	// Checks whether the game is over
	@Override
	public boolean isGameOver() {

		int count = 0;
		int noValidMovesCount = 0;

		// If none of the detectives have any tickets available then the game is over and Mr. X wins
		for(ScotlandYardPlayer p : players) {
			if(p.isDetective()) {
				count ++;
				if(!p.hasTickets(TAXI)) {
					if(!p.hasTickets(BUS)) {
						if(!p.hasTickets(UNDERGROUND)) {
							noValidMovesCount ++;
						}
					}
				}
			}
		}

		if(noValidMovesCount == count && count >= 1) {
			return true;
		}

		count = 0;
		noValidMovesCount = 0;

		// Game over if max rounds is reached
		if (roundNumber >= rounds.size()) {
			detectivesWin = false;
			return true;
		}

		Colour black = Colour.BLACK;
		Optional<ScotlandYardPlayer> xOptional = getPlayer(black);
		ScotlandYardPlayer x = xOptional.get();

		// Game over if a detective lands on mr x
		for (ScotlandYardPlayer player : players) {
			if (player.colour().isDetective()) {
				if (player.location() == x.location()) {
					detectivesWin = true;

					return true;
				}
			}
		}

		// Game is over if mr x is stuck
		for (ScotlandYardPlayer player : players) {

			if (player.colour() == BLACK) {
				if (checkValidMoves(playerOrder).isEmpty()) {
					detectivesWin = true;

					return true;
				}
			} else {
				count += 1;
				Colour c = player.colour();
				Move pass = new PassMove(c);
				if (checkValidMoves(playerOrder).isEmpty() || checkValidMoves(playerOrder).iterator().next() == pass) {
					noValidMovesCount += 1;
				}
			}
		}

		detectivesWin = false;

		return false;
	}

	// Returns a certain player given the colour
	private Optional<ScotlandYardPlayer> getPlayer(Colour colour) {
		for (ScotlandYardPlayer player : players) {
			if (player.colour() == colour) {
				return Optional.of(player);
			}
		}
		return Optional.empty();
	}

	// Returns the current player
	@Override
	public Colour getCurrentPlayer() {
		return players.get(playerOrder).colour();
	}

	// Returns current round
	@Override
	public int getCurrentRound() {
		return this.roundNumber;
	}

	// Returns all rounds, true or false whether it is a reveal round
	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	// Returns an immutable graph
	@Override
	public Graph<Integer, Transport> getGraph() {
		ImmutableGraph<Integer, Transport> graph2 = new ImmutableGraph<>(this.graph);
		return graph2;
	}

	// Called after a move is chosen
	@Override
	public void accept(Move move) {

		requireNonNull(move);

		// Check that the move is a valid move
		if (!validMoves.contains(move)) {
			throw new IllegalArgumentException("Move not valid");
		}

		// Call the visit method - Makes use of polymorphism depending on the type of ticket within the move
		move.visit(this);

		ScotlandYardPlayer currentPlayer = players.get(playerOrder);

		// If the current player is Mr X then the round rotation is complete and notify the spectators
		if (currentPlayer.isMrX()) {
			for (Spectator spectator : spectators) {
				spectator.onRotationComplete(this);
			}
		}

		// If it's not Mr X, allow the other players to execute their moves
		if (!currentPlayer.isMrX()) {

			// Fetch valid moves for that current player
			validMoves = checkValidMoves(playerOrder);

			if (!isGameOver()) {
				currentPlayer.player().makeMove(this, currentPlayer.location(), validMoves, this);
			}
		}

	}

	// Execute the move
	@Override
	public void visit(DoubleMove move) {
		// Don't allow double move if the round number is within 2 from the end
		if(roundNumber < getRounds().size() - 2) {
			DoubleMove temp = move;
			DoubleMove consumerMove;
			TicketMove consumerFirstMove;
			TicketMove consumerSecondMove;

			ScotlandYardPlayer player = players.get(playerOrder);
			player.removeTicket(DOUBLE);

			// Depending on if the current round and/or the next round is a hidden move,
			// edit the DoubleMove to use the lastKnownLocation within the destination parameter
			if (!getRounds().get(roundNumber) && !getRounds().get(roundNumber + 1)) {
				consumerFirstMove = new TicketMove(BLACK, move.firstMove().ticket(), lastKnownLocation);
				consumerSecondMove = new TicketMove(BLACK, move.secondMove().ticket(), lastKnownLocation);
				consumerMove = new DoubleMove(BLACK, consumerFirstMove, consumerSecondMove);
				//move = new DoubleMove(BLACK, consumerFirstMove, consumerSecondMove);
			} else if (!getRounds().get(roundNumber)) {
				consumerFirstMove = new TicketMove(BLACK, move.firstMove().ticket(), lastKnownLocation);
				consumerMove = new DoubleMove(BLACK, consumerFirstMove, temp.secondMove());
			} else if (!getRounds().get(roundNumber + 1)) {
				consumerSecondMove = new TicketMove(BLACK, move.secondMove().ticket(), move.firstMove().destination());
				consumerMove = new DoubleMove(BLACK, temp.firstMove(), consumerSecondMove);
			} else {
				consumerMove = new DoubleMove(BLACK, temp.firstMove(), temp.secondMove());
			}

			// Notify the spectators of the double move that is about to be played
			for (Spectator spectator : spectators) {
				spectator.onMoveMade(this, consumerMove);
			}

			// Change the players location to the destination of the first move
			player.location(move.firstMove().destination());

			// Remove the relevant ticket type
			player.removeTicket(move.firstMove().ticket());

			// Increment round number
			roundNumber++;

			// If it is a reveal round, update the last known location to Mr X's current location
			if (getRounds().get(roundNumber)) {
				lastKnownLocation = player.location();
			}

			// Notify the spectators that the round has started
			for (Spectator spectator : spectators) {
				spectator.onRoundStarted(this, roundNumber);
			}

			// Notify the spectators that the first move of the double move has been executed
			for (Spectator spectator : spectators) {
				spectator.onMoveMade(this, consumerMove.firstMove());
			}

			// Execute the second move of the double move
			player.location(move.secondMove().destination());
			player.removeTicket(move.secondMove().ticket());
			roundNumber++;

			if (getRounds().get(roundNumber)) {
				lastKnownLocation = player.location();
			}

			playerOrder++;

			for (Spectator spectator : spectators) {
				spectator.onRoundStarted(this, roundNumber);
			}

			for (Spectator spectator : spectators) {
				spectator.onMoveMade(this, consumerMove.secondMove());
			}
		}
	}

	@Override
	public void visit(TicketMove move) {
		ScotlandYardPlayer player = players.get(playerOrder);

		// Move the player to the destination of the ticket move
		player.location(move.destination());

		// Remove the ticket from the player's arsenal
		player.removeTicket(move.ticket());

		// If the player is a detective
		if (player.isDetective()) {
			// Give the ticket to Mr X
			players.get(0).addTicket(move.ticket());
		} else {
			// If it's a reveal round update Mr X's last known location
			if(getRounds().get(roundNumber)) {
				lastKnownLocation = player.location();
			}
			if(!getRounds().get(roundNumber)) {
				move = new TicketMove(BLACK, move.ticket(), lastKnownLocation);
			}
			roundNumber++;
		}

		// Increment the playerOrder, if it has reached the last detective, then set it to 0 to restart the rotation
		if (playerOrder == players.size() - 1) {
			playerOrder = 0;
		} else {
			playerOrder++;
		}

		// If the game is over
		if(isGameOver()) {

			// Notify the spectators of the move made
			for (Spectator spectator : spectators) {
				spectator.onMoveMade(this, move);
			}

			// Notify the spectators that the game is over
			for (Spectator spectator : spectators) {
				spectator.onGameOver(this, getWinningPlayers());
			}

		} else {
			for (Spectator spectator : spectators) {
				spectator.onMoveMade(this, move);
			}
		}

		if (playerOrder == 1 && roundNumber > 0) {
			// If it is now the first detective's turn, notify the spectators that a new round has started
			for (Spectator spectator : spectators) {
				spectator.onRoundStarted(this, roundNumber);
			}
		}
	}

	@Override
	public void visit(PassMove move) {

		// Increment player order
		if (playerOrder == players.size() - 1) {
			playerOrder = 0;
		} else {
			playerOrder++;
		}

		// If it is now the first detective's turn, notify the spectators that a new round has started
		if (playerOrder == 1) {
			roundNumber++;
			if (roundNumber > 0) {
				for (Spectator spectator : spectators) {
					spectator.onRoundStarted(this, roundNumber);
				}
			}
		}

		for (Spectator spectator : spectators) {
			spectator.onMoveMade(this, move);
		}
	}
}

//	private boolean hasValidMoves(Set<Move> moves) {
//		Colour blu = Colour.BLUE;
//		Colour gre = Colour.GREEN;
//		Colour re = Colour.RED;
//		Colour whi = Colour.WHITE;
//		Colour yel = Colour.YELLOW;
//		Move blue = new PassMove(blu);
//		Move green = new PassMove(gre);
//		Move red = new PassMove(re);
//		Move white = new PassMove(whi);
//		Move yellow = new PassMove(yel);
//		if(moves.isEmpty() || moves.iterator().next() == blue || moves.iterator().next() == green || moves.iterator().next() == red || moves.iterator().next() == white || moves.iterator().next() == yellow) {
//			return false;
//		}else {
//			return true;
//		}
//	}


