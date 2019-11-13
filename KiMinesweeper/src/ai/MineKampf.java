package ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.reader.DimacsReader;
import org.sat4j.reader.Reader;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;
import api.MSAgent;
import api.MSField;

public class MineKampf extends MSAgent {

  private boolean displayActivated = false;
  private boolean firstDecision = true;
  
  private int[][] currentField;
  private boolean[][] edgeField;
  List<int[]> binaryStrings;
  private LinkedList<Position> masterQueue;

  public MineKampf(MSField field) {
    super(field);
    this.masterQueue = new LinkedList<>();
    
    currentField = new int[field.getNumOfCols()][field.getNumOfRows()];
    for (int i = 0; i < field.getNumOfCols(); i++) {
      for (int j = 0; j < field.getNumOfRows(); j++) {
        currentField[i][j] = -2;
      }
    }
    
    edgeField = new boolean[field.getNumOfCols()][field.getNumOfRows()];
    for (int i = 0; i < field.getNumOfCols(); i++) {
      for (int j = 0; j < field.getNumOfRows(); j++) {
        edgeField[i][j] = false;
      }
    }
  }

  @Override
  public boolean solve() {

    int numOfRows = this.field.getNumOfRows();
    int numOfCols = this.field.getNumOfCols();
    int x, y, feedback;
    Position p;

    do {
      if (displayActivated) {
        System.out.println(field);
      }

      if (firstDecision) {
        x = 0;
        y = 0;
        edgeField[x][y] = true;
        firstDecision = false;
      } else {
        p = this.nextPosition(numOfRows, numOfCols);
        x = p.getX();
        y = p.getY();
      }

      if (displayActivated)
        System.out.println("Uncovering (" + x + "," + y + ")");
      feedback = field.uncover(x, y);
      currentField[x][y] = feedback;
      
    } while (feedback >= 0 && !field.solved());

    if (field.solved()) {
      if (displayActivated) {
        System.out.println("Solved the field");
      }
      return true;
    } else {
      if (displayActivated) {
        System.out.println("BOOM!");
      }
      return false;
    }
  }

  public void activateDisplay() {
    this.displayActivated = true;

  }

  public void deactivateDisplay() {
    this.displayActivated = false;
  }
  
  private Position nextPosition(int numOfRows, int numOfCols) {
    Position p;
    LinkedList<Position> queue = new LinkedList<Position>();
    
    for (int i = 0; i < field.getNumOfCols(); i++) {
      for (int j = 0; j < field.getNumOfRows(); j++) {
        if (edgeField[i][j]) {
          queue.add(new Position(i,j));
        }
      }
    }
    
    for(Iterator<Position> it = queue.iterator(); it.hasNext();) {
      Position help = it.next();
      int square = currentField[help.getX()][help.getY()];
      switch (square) {
        case -1:
          // mit Flage markiert
          break;
        case 0:
          getNeighbors0(help.getX(), help.getY());
          break;
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
          //benötigte infos: welche Nachbarn kennen wir schon? wie viel Quadrate noch unaufgedeckt? 
          getNeighbors(help.getX(), help.getY(), square);
          break;
        case 8:
          getNeighbors8(help.getX(), help.getY());
          break;
        default:
          System.out.println("Fehler");
          break;
      }
    }
    
    return null;
  }
  
  private void getNeighbors(int x, int y, int numberOfBombes) {
    ArrayList<Position> unknown = new ArrayList<Position>();
    for (int i = -1; i < 2; i++) {
      for (int j = -1; j < 2; j++) {
        if ((i != 0 || j != 0) && validPosition(x + i, y + j)) {
          if (currentField[x + i][y + j] == -2) {
            unknown.add(new Position(x + i, y + j));
          } else if (currentField[x + i][y + j] == -1) {
            numberOfBombes--;
          }
        }
      }
    }
    if (unknown.size() == numberOfBombes) {
      //gehe Positions durch und setze auf -1
      for (Iterator<Position> it = unknown.iterator(); it.hasNext();) {
        Position n = it.next();
        currentField[n.getX()][n.getY()] = -1;
      }
    } else if (unknown.size() < numberOfBombes) {
      //Es ist ein Fehler aufgetreten
      System.out.println("Konsistenzfehler");
    } else {
      //SAT solver
      final int MAXVAR = 1000000;
      final int NBCLAUSES = (int) Math.pow(2, unknown.size());
   
      ISolver solver = SolverFactory.newDefault();
      solver.newVar(MAXVAR);
      solver.setExpectedNumberOfClauses(NBCLAUSES);

      List<int []> clauses = getClause(numberOfBombes, unknown.size());
      for (Iterator<int[]> it = clauses.iterator(); it.hasNext();) {
        int[] clause = (int[]) it.next();
        try {
          solver.addClause(new VecInt(clause));
        } catch (ContradictionException e) {
          e.printStackTrace();
        }
      }
           
      try {
        IProblem problem = solver;
        if (problem.isSatisfiable()) {
          problem.findModel();
        } else {

        }
      } catch (TimeoutException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  
  private void getNeighbors0(int x, int y) {
    for (int i = -1; i < 2; i++) {
      for (int j = -1; j < 2; j++) {
        if ((i != 0 || j != 0) && validPosition(x + i, y + j) && currentField[x + i][y + j] == -2) {
          this.masterQueue.add(new Position(x + i, y + j));
        }
      }
    }
  }
  
  private void getNeighbors8(int x, int y) {
    for (int i = -1; i < 2; i++) {
      for (int j = -1; j < 2; j++) {
        if ((i != 0 || j != 0) && validPosition(x + i, y + j) && currentField[x + i][y + j] == -2) {
          this.currentField[x + i][y + j] = -1;
        }
      }
    }
  }
  
  private boolean validPosition(int x, int y) {
    if (x >= 0 && x < this.field.getNumOfCols() && y >= 0 && y < this.field.getNumOfRows()) {
      return true;
    }
    return false;
  }
  
  private List<int[]> getClause(int numberOfBombes, int numberOfUnknownNeighbours) {
    this.binaryStrings = new ArrayList<int[]>();
    List<int []> result = new ArrayList<>();
    int[] arr = new int[numberOfUnknownNeighbours];
    this.generateAllBinaryStrings(numberOfUnknownNeighbours, arr, 0);
    
    for (Iterator<int[]> it = binaryStrings.iterator(); it.hasNext();) {
      int [] binary = (int[]) it.next();
      byte countNegativ = 0;
      for (int i = 0; i < binary.length; i++) {
        //invertieren + umwandeln in Variablen für SAT Solver
        if (binary[i] == 0) {
          binary[i] = (i + 1);
        } else {
          binary[i] = (-1) * (i + 1);
          countNegativ++;
        }
      } 
      //herausnehmen der korrekten Belegungen
      if (countNegativ != numberOfBombes) {
        result.add(binary);
      }
    }
    
    return result;
  }
  
  private void generateAllBinaryStrings(int n, int[] arr, int i) {
    if (i == n) {
      binaryStrings.add(arr);
      return;
    }
    arr[i] = 0;
    generateAllBinaryStrings(n, arr, i + 1);

    arr[i] = 1;
    generateAllBinaryStrings(n, arr, i + 1);
  }
  
  class Position {
    private int x, y;
    
    public Position(int x, int y) {
      this.x = x;
      this.y =y;
    }
    
    public int getX() {
      return this.x;
    }
    
    public int getY() {
      return this.y;
    }
  }
}
