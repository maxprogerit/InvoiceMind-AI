class ApiError(Exception):
    """Domain error mapped to a JSON body {"error": {"code", "message", "request_id"}}."""

    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
