package domain;

public class BaseEntity<TId> {
  private final TId id;

  protected BaseEntity(TId id) {
    this.id = DomainValidation.requireNonNull(id, "Entity id");
  }

  public TId getId() {
    return id;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof BaseEntity<?> entity && id.equals(entity.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
