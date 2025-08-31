import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Card,
  Button,
  Table,
  Spinner,
  Alert,
  Row,
  Col,
  Modal,
  Badge,
  Pagination,
  Form,
  InputGroup,
  ButtonGroup,
  Dropdown,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faEye,
  faTrash,
  faPlus,
  faImages,
  faSearch,
  faThLarge,
  faList,
  faCalendarAlt,
  faUser,
  faChevronDown,
} from "@fortawesome/free-solid-svg-icons";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";

const InspectionList = () => {
  const [inspections, setInspections] = useState([]);
  const [filteredInspections, setFilteredInspections] = useState([]);
  const [transformers, setTransformers] = useState([]);
  const [selectedTransformer, setSelectedTransformer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadingTransformers, setLoadingTransformers] = useState(true);
  const [error, setError] = useState("");
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [inspectionToDelete, setInspectionToDelete] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage] = useState(6);
  const [searchTerm, setSearchTerm] = useState("");
  const [viewMode, setViewMode] = useState("cards");

  const navigate = useNavigate();
  const { token, isAuthenticated } = useAuth();

  useEffect(() => {
    fetchTransformers();
  }, [token]);

  useEffect(() => {
    if (selectedTransformer) {
      fetchInspections(selectedTransformer.id);
    }
  }, [selectedTransformer, token]);

  const fetchTransformers = async () => {
    try {
      setLoadingTransformers(true);
      const response = await axios.get(
        "http://localhost:8080/api/transformer-records",
        isAuthenticated ? { headers: { Authorization: `Bearer ${token}` } } : {}
      );
      setTransformers(response.data);
      if (response.data.length > 0) {
        setSelectedTransformer(response.data[0]);
      }
      setError("");
    } catch (err) {
      setError("Failed to fetch transformers");
    } finally {
      setLoadingTransformers(false);
    }
  };

  const fetchInspections = async (transformerId) => {
    try {
      setLoading(true);
      const response = await axios.get(
        `http://localhost:8080/api/inspections/transformer/${transformerId}`,
        isAuthenticated ? { headers: { Authorization: `Bearer ${token}` } } : {}
      );
      setInspections(response.data);
      setFilteredInspections(response.data);
      setError("");
    } catch (err) {
      setError("Failed to fetch inspections");
      setInspections([]);
      setFilteredInspections([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let result = [...inspections];

    // Apply search filter
    if (searchTerm) {
      result = result.filter((inspection) => {
        return (
          inspection.notes?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          inspection.conductedBy?.displayName
            ?.toLowerCase()
            .includes(searchTerm.toLowerCase())
        );
      });
    }

    setFilteredInspections(result);
    setCurrentPage(1);
  }, [inspections, searchTerm]);

  const handleDelete = async (id) => {
    try {
      await axios.delete(`http://localhost:8080/api/inspections/${id}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      fetchInspections(selectedTransformer.id);
    } catch (err) {
      setError("Failed to delete inspection");
    } finally {
      setShowDeleteModal(false);
    }
  };

  // Pagination logic
  const indexOfLastItem = currentPage * itemsPerPage;
  const indexOfFirstItem = indexOfLastItem - itemsPerPage;
  const currentItems = filteredInspections.slice(
    indexOfFirstItem,
    indexOfLastItem
  );
  const totalPages = Math.ceil(filteredInspections.length / itemsPerPage);

  if (loadingTransformers) {
    return (
      <div className="text-center mt-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading transformers...</p>
      </div>
    );
  }

  if (error && !selectedTransformer) {
    return (
      <Alert variant="danger" className="mt-4">
        {error}
      </Alert>
    );
  }

  return (
    <div className="moodle-container">
      <div className="page-header d-flex justify-content-between align-items-center">
        <h2>Transformer Inspections</h2>
        <div className="d-flex align-items-center">
          <ButtonGroup className="me-3">
            <Button
              variant={viewMode === "cards" ? "primary" : "outline-secondary"}
              onClick={() => setViewMode("cards")}
              size="sm"
            >
              <FontAwesomeIcon icon={faThLarge} />
            </Button>
            <Button
              variant={viewMode === "table" ? "primary" : "outline-secondary"}
              onClick={() => setViewMode("table")}
              size="sm"
            >
              <FontAwesomeIcon icon={faList} />
            </Button>
          </ButtonGroup>
          {isAuthenticated && selectedTransformer && (
            <Button
              variant="primary"
              onClick={() =>
                navigate(`/inspections/add/${selectedTransformer.id}`)
              }
            >
              <FontAwesomeIcon icon={faPlus} className="me-2" />
              Add New Inspection
            </Button>
          )}
        </div>
      </div>

      {/* Transformer Selection */}
      <Card className="mb-4">
        <Card.Body>
          {/* Change: Use a single Row and two Columns to align horizontally */}
          <Row className="g-3 align-items-center">
            <Col md={6}>
              <Form.Label>Select Transformer</Form.Label>
              {/* Change: Use InputGroup with Dropdown to align better */}
              <InputGroup>
                {/* Change: Remove the FontAwesomeIcon from here, as the dropdown already provides an arrow. */}
                <Dropdown className="w-100">
                  <Dropdown.Toggle
                    variant="outline-secondary"
                    className="w-100 text-start"
                  >
                    {selectedTransformer ? (
                      <>
                        {selectedTransformer.name}
                        {selectedTransformer.poleNo &&
                          ` (Pole #${selectedTransformer.poleNo})`}
                      </>
                    ) : (
                      "Select a transformer"
                    )}
                  </Dropdown.Toggle>
                  <Dropdown.Menu className="w-100">
                    {transformers.map((transformer) => (
                      <Dropdown.Item
                        key={transformer.id}
                        onClick={() => setSelectedTransformer(transformer)}
                        active={
                          selectedTransformer &&
                          selectedTransformer.id === transformer.id
                        }
                      >
                        {transformer.name}
                        {transformer.poleNo && ` (Pole #${transformer.poleNo})`}
                      </Dropdown.Item>
                    ))}
                  </Dropdown.Menu>
                </Dropdown>
              </InputGroup>
            </Col>
            <Col md={6}>
              {/* Change: Add Form.Label for consistency and proper spacing */}
              <Form.Label>Search</Form.Label>
              <InputGroup>
                <Form.Control
                  type="text"
                  placeholder="Search by notes or admin..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
                <Button variant="outline-secondary">
                  <FontAwesomeIcon icon={faSearch} />
                </Button>
              </InputGroup>
            </Col>
          </Row>
        </Card.Body>
      </Card>

      {!selectedTransformer ? (
        <div className="text-center py-4">
          <FontAwesomeIcon
            icon={faImages}
            size="3x"
            className="text-muted mb-3"
          />
          <p>No transformers available</p>
        </div>
      ) : loading ? (
        <div className="text-center mt-5">
          <Spinner animation="border" variant="primary" />
          <p className="mt-2">Loading inspections...</p>
        </div>
      ) : filteredInspections.length === 0 ? (
        <div className="text-center py-4">
          <FontAwesomeIcon
            icon={faImages}
            size="3x"
            className="text-muted mb-3"
          />
          <p>No inspections found for this transformer</p>
          {isAuthenticated && (
            <Button
              variant="primary"
              onClick={() =>
                navigate(`/inspections/add/${selectedTransformer.id}`)
              }
            >
              Add First Inspection
            </Button>
          )}
        </div>
      ) : viewMode === "table" ? (
        <>
          <Card className="mb-4">
            <Card.Body>
              <Table striped hover responsive className="moodle-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Conducted By</th>
                    <th>Notes</th>
                    <th>Images</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {currentItems.map((inspection) => (
                    <tr key={inspection.id}>
                      <td>
                        <FontAwesomeIcon
                          icon={faCalendarAlt}
                          className="me-2 text-muted"
                        />
                        {new Date(inspection.createdAt).toLocaleDateString()}
                      </td>
                      <td>
                        <FontAwesomeIcon
                          icon={faUser}
                          className="me-2 text-muted"
                        />
                        {inspection.conductedBy?.displayName || "-"}
                      </td>
                      <td>{inspection.notes || "-"}</td>
                      <td>
                        <Badge bg="secondary">
                          {inspection.images?.length || 0}
                        </Badge>
                      </td>
                      <td>
                        <Button
                          variant="outline-primary"
                          size="sm"
                          onClick={() =>
                            navigate(`/inspections/${inspection.id}`)
                          }
                          className="me-2"
                        >
                          <FontAwesomeIcon icon={faEye} />
                        </Button>
                        {isAuthenticated && (
                          <Button
                            variant="outline-danger"
                            size="sm"
                            onClick={() => {
                              setInspectionToDelete(inspection.id);
                              setShowDeleteModal(true);
                            }}
                          >
                            <FontAwesomeIcon icon={faTrash} />
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </Card.Body>
          </Card>
        </>
      ) : (
        <>
          <Row xs={1} md={2} lg={3} className="g-4">
            {currentItems.map((inspection) => (
              <Col key={inspection.id}>
                <Card className="h-100 bg-light">
                  <Card.Body>
                    <Card.Title className="d-flex justify-content-between align-items-start">
                      Inspection #{inspection.id}
                      <Badge bg="primary">
                        {new Date(inspection.createdAt).toLocaleDateString()}
                      </Badge>
                    </Card.Title>

                    <div className="d-flex justify-content-between text-muted mb-3">
                      <div>
                        <FontAwesomeIcon icon={faUser} className="me-2" />
                        <small>
                          {inspection.conductedBy?.displayName || "Unknown"}
                        </small>
                      </div>
                    </div>

                    {inspection.notes && (
                      <Card.Text className="mb-3">
                        {inspection.notes.length > 100
                          ? `${inspection.notes.substring(0, 100)}...`
                          : inspection.notes}
                      </Card.Text>
                    )}

                    <div className="d-flex justify-content-between align-items-center">
                      <Badge bg="secondary">
                        {inspection.images?.length || 0} images
                      </Badge>

                      <div>
                        <Button
                          variant="outline-primary"
                          size="sm"
                          onClick={() =>
                            navigate(`/inspections/${inspection.id}`)
                          }
                          className="me-2"
                        >
                          <FontAwesomeIcon icon={faEye} />
                        </Button>
                        {isAuthenticated && (
                          <Button
                            variant="outline-danger"
                            size="sm"
                            onClick={() => {
                              setInspectionToDelete(inspection.id);
                              setShowDeleteModal(true);
                            }}
                          >
                            <FontAwesomeIcon icon={faTrash} />
                          </Button>
                        )}
                      </div>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            ))}
          </Row>
        </>
      )}

      {totalPages > 1 && (
        <div className="d-flex justify-content-center mt-4">
          <Pagination>
            <Pagination.First
              onClick={() => setCurrentPage(1)}
              disabled={currentPage === 1}
            />
            <Pagination.Prev
              onClick={() => setCurrentPage(currentPage - 1)}
              disabled={currentPage === 1}
            />
            {[...Array(totalPages).keys()].map((number) => {
              const page = number + 1;
              if (
                page === 1 ||
                page === totalPages ||
                (page >= currentPage - 1 && page <= currentPage + 1)
              ) {
                return (
                  <Pagination.Item
                    key={page}
                    active={page === currentPage}
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </Pagination.Item>
                );
              } else if (
                (page === currentPage - 2 && currentPage > 3) ||
                (page === currentPage + 2 && currentPage < totalPages - 2)
              ) {
                return <Pagination.Ellipsis key={page} />;
              }
              return null;
            })}
            <Pagination.Next
              onClick={() => setCurrentPage(currentPage + 1)}
              disabled={currentPage === totalPages}
            />
            <Pagination.Last
              onClick={() => setCurrentPage(totalPages)}
              disabled={currentPage === totalPages}
            />
          </Pagination>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <Modal show={showDeleteModal} onHide={() => setShowDeleteModal(false)}>
        <Modal.Header closeButton>
          <Modal.Title>Confirm Deletion</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Are you sure you want to delete this inspection? This action cannot be
          undone.
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowDeleteModal(false)}>
            Cancel
          </Button>
          <Button
            variant="danger"
            onClick={() => handleDelete(inspectionToDelete)}
          >
            Delete
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default InspectionList;
