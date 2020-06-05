import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormHelperText,
  makeStyles,
  Switch,
  TextField,
  Theme,
} from '@material-ui/core';
import { createList, USER_SELF_CREATE_LIST } from '../../actions/lists';
import CreateAListValidator from '../../utils/validation/CreateAListValidator';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../../hooks/useStateSelector';
import dequal from 'dequal';
import { useWithUserContext } from '../../hooks/useWithUser';
import { useDispatchAction } from '../../hooks/useDispatchAction';
import { logModalView } from '../../utils/analytics';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    button: {
      margin: theme.spacing(1),
      whiteSpace: 'nowrap',
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
  }),
);

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function CreateListDialog(props: Props) {
  const classes = useStyles();

  const [listName, setListName] = useState('');
  const [nameLengthError, setNameLengthError] = useState(false);
  const [nameDuplicateError, setNameDuplicateError] = useState(false);
  const [isLoading, wasLoading] = useStateSelectorWithPrevious(
    state => state.userSelf.loading[USER_SELF_CREATE_LIST],
  );
  const listsById = useStateSelector(state => state.lists.listsById, dequal);
  const { isLoggedIn } = useWithUserContext();

  const dispatchCreateList = useDispatchAction(createList);

  useEffect(() => {
    if (props.open) {
      logModalView('CreateListDialog');
    }
  }, [props.open]);

  useEffect(() => {
    if (wasLoading && !isLoading) {
    }
  }, [isLoading]);

  const updateListNameCb = useCallback(
    (ev: React.ChangeEvent<HTMLInputElement>) => {
      setListName(ev.target.value);
    },
    [listName],
  );

  const handleModalClose = () => {
    setListName('');
    setNameLengthError(false);
    setNameDuplicateError(false);
    props.onClose();
  };

  const handleMakeListPublic = () => {
    if (isLoggedIn) {
      // dispatchUpdateList({
      //   listId: listId,
      //   name: newListName,
      // });
    }
  };

  const validateListNameAndCreate = () => {
    // Reset error states before validation
    if (isLoggedIn) {
      let validationResult = CreateAListValidator.validate(listsById, listName);

      if (validationResult.hasError()) {
        let {
          nameDuplicateError,
          nameLengthError,
        } = validationResult.asObject();
        setNameDuplicateError(nameDuplicateError);
        setNameLengthError(nameLengthError);
      } else {
        setNameLengthError(false);
        setNameDuplicateError(false);
        handleCreateListSubmit();
      }
    }
  };

  const handleCreateListSubmit = () => {
    dispatchCreateList({ name: listName });
    handleModalClose();
  };

  return (
    <Dialog fullWidth maxWidth="xs" open={props.open}>
      <DialogTitle className={classes.title}>Create New List</DialogTitle>
      <DialogContent>
        <FormControl fullWidth>
          <TextField
            autoFocus
            margin="dense"
            id="name"
            label="Name"
            type="text"
            fullWidth
            value={listName}
            error={nameDuplicateError || nameLengthError}
            onChange={updateListNameCb}
          />
          <FormHelperText
            id="component-error-text"
            style={{
              display: nameDuplicateError || nameLengthError ? 'block' : 'none',
            }}
          >
            {nameLengthError ? 'List name cannot be blank' : null}
            {nameDuplicateError
              ? 'You already have a list with this name'
              : null}
          </FormHelperText>
          {/* <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Making this list public will allow anyone with the URL to view
              your list. This can be changed later.
            </DialogContentText>
            <FormControlLabel
              control={
                <Switch
                  onChange={() => handleMakeListPublic()}
                  value="checked"
                  color="primary"
                />
              }
              label="Make List Public"
            />
          </DialogContent> */}
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button
          disabled={isLoading}
          onClick={handleModalClose}
          className={classes.button}
        >
          Cancel
        </Button>
        <Button
          disabled={isLoading}
          onClick={validateListNameAndCreate}
          color="primary"
          variant="contained"
          className={classes.button}
        >
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
