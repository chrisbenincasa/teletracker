import {
  Button,
  createStyles,
  Dialog,
  DialogActions,
  DialogTitle,
  DialogContent,
  FormControl,
  FormHelperText,
  TextField,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import withUser, { WithUserProps } from '../components/withUser';
import { Loading } from '../reducers/user';
import {
  createList,
  USER_SELF_CREATE_LIST,
  UserCreateListPayload,
} from '../actions/lists';
import { bindActionCreators, Dispatch } from 'redux';
import { connect } from 'react-redux';
import { ListsByIdMap } from '../reducers/lists';
import { AppState } from '../reducers';
import CreateAListValidator from '../utils/validation/CreateAListValidator';

const styles = (theme: Theme) => createStyles({});

interface DispatchProps {
  createList: (payload?: UserCreateListPayload) => void;
}

interface OwnProps extends WithStyles<typeof styles> {
  listsById: ListsByIdMap;
  loading: Partial<Loading>;
  onClose: () => void;
  open: boolean;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

interface State {
  listName: string;
  nameLengthError: boolean;
  nameDuplicateError: boolean;
}

class CreateListDialog extends Component<Props, State> {
  constructor(props) {
    super(props);

    this.state = {
      listName: '',
      nameLengthError: false,
      nameDuplicateError: false,
    };
  }

  componentDidUpdate(oldProps: Props) {
    if (
      Boolean(oldProps.loading[USER_SELF_CREATE_LIST]) &&
      !Boolean(this.props.loading[USER_SELF_CREATE_LIST])
    ) {
      this.handleModalClose();
    }
  }

  handleModalClose = () => {
    this.setState({
      listName: '',
      nameLengthError: false,
      nameDuplicateError: false,
    });
    this.props.onClose();
  };

  validateListName = () => {
    let { listsById, userSelf } = this.props;
    let { listName } = this.state;

    // Reset error states before validation
    if (userSelf) {
      this.setState(CreateAListValidator.defaultState().asObject());

      let validationResult = CreateAListValidator.validate(listsById, listName);

      if (validationResult.hasError()) {
        this.setState(validationResult.asObject());
      } else {
        this.handleCreateListSubmit();
      }
    }
  };

  handleCreateListSubmit = () => {
    let { createList } = this.props;
    let { listName } = this.state;

    createList({ name: listName });
    this.handleModalClose();
  };

  renderDialog() {
    let { loading } = this.props;
    let { listName, nameDuplicateError, nameLengthError } = this.state;
    let isLoading = Boolean(loading[USER_SELF_CREATE_LIST]);

    return (
      <Dialog fullWidth maxWidth="xs" open={this.props.open}>
        <DialogTitle>Create New List</DialogTitle>
        <DialogContent>
          <FormControl style={{ width: '100%' }}>
            <TextField
              autoFocus
              margin="dense"
              id="name"
              label="Name"
              type="text"
              fullWidth
              value={listName}
              error={nameDuplicateError || nameLengthError}
              onChange={e => this.setState({ listName: e.target.value })}
            />
            <FormHelperText
              id="component-error-text"
              style={{
                display:
                  nameDuplicateError || nameLengthError ? 'block' : 'none',
              }}
            >
              {nameLengthError ? 'List name cannot be blank' : null}
              {nameDuplicateError
                ? 'You already have a list with this name'
                : null}
            </FormHelperText>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button
            disabled={isLoading}
            onClick={this.handleModalClose}
            color="primary"
          >
            Cancel
          </Button>
          <Button
            disabled={isLoading}
            onClick={this.validateListName}
            color="secondary"
            variant="contained"
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  render() {
    return this.renderDialog();
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    listsById: appState.lists.listsById,
    loading: appState.userSelf.loading,
  };
};

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      createList,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(CreateListDialog),
  ),
);
