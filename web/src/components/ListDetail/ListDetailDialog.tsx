import { useRouter } from 'next/router';
import React, { useEffect, useState } from 'react';
import { useWithUserContext } from '../../hooks/useWithUser';
import useStateSelector from '../../hooks/useStateSelector';
import { useDispatchAction } from '../../hooks/useDispatchAction';
import { deleteList } from '../../actions/lists';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
} from '@material-ui/core';
import _ from 'lodash';
import { List } from '../../types';
import useStyles from './ListDetail.styles';

interface ListDetailDialogProps {
  readonly list?: List;
  readonly openDeleteConfirmation: boolean;
}

export default function ListDetailDialog(props: ListDetailDialogProps) {
  const classes = useStyles();
  const router = useRouter();
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(
    props.openDeleteConfirmation,
  );
  const [migrateListId, setMigrateListId] = useState('');

  const { isLoggedIn } = useWithUserContext();
  const listsById = useStateSelector(state => state.lists.listsById);
  const { list } = props;

  useEffect(() => {
    setDeleteConfirmationOpen(props.openDeleteConfirmation);
  }, [props.openDeleteConfirmation]);

  const handleMigration = event => {
    setMigrateListId(event.target.value);
  };

  const handleDeleteModalClose = () => {
    setDeleteConfirmationOpen(false);
  };

  const dispatchDeleteList = useDispatchAction(deleteList);

  const handleDeleteList = () => {
    if (isLoggedIn) {
      const migrationId = migrateListId !== '0' ? migrateListId : undefined;

      dispatchDeleteList({
        listId: list!.id,
        mergeListId: migrationId,
      });

      // TODO: Loading state.
      router.push('/');
    }

    // TODO: Handle this better - tell them they can't.
    handleDeleteModalClose();
  };

  return (
    <div>
      <Dialog
        open={deleteConfirmationOpen}
        onClose={() => setDeleteConfirmationOpen(false)}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title" className={classes.title}>
          {`Delete "${list?.name}" list?`}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            {`Are you sure you want to delete this list? There is no way to undo
              this. ${
                list && list.totalItems > 0
                  ? 'All of the content from your list can be deleted or migrated to another list.'
                  : ''
              }`}
          </DialogContentText>
          {list && list.totalItems > 0 && (
            <FormControl className={classes.formControl}>
              <InputLabel htmlFor="list-items">
                Move items from this list to:
              </InputLabel>
              <Select
                value={migrateListId}
                onChange={handleMigration}
                id="list-items"
              >
                <MenuItem key="delete" value="0">
                  <em>Delete these items</em>
                </MenuItem>
                {isLoggedIn &&
                  _.map(
                    listsById,
                    item =>
                      item.id !== list?.id && (
                        <MenuItem key={item.id} value={item.id}>
                          {item.name}
                        </MenuItem>
                      ),
                  )}
              </Select>
            </FormControl>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setDeleteConfirmationOpen(false)}
            color="primary"
          >
            Cancel
          </Button>
          <Button
            onClick={handleDeleteList}
            color="primary"
            variant="contained"
            autoFocus
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}
