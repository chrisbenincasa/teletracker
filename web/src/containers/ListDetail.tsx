import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  FormControlLabel,
  IconButton,
  LinearProgress,
  ListItemIcon,
  Menu,
  MenuItem,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@material-ui/core';
import {
  Delete,
  Edit,
  OfflineBolt,
  Public,
  Settings,
} from '@material-ui/icons';
import _ from 'lodash';
import React, { useCallback, useEffect, useState } from 'react';
import { LIST_RETRIEVE_INITIATED, getList, updateList } from '../actions/lists';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import ScrollToTopContainer from '../components/ScrollToTopContainer';
import { List } from '../types';
import { smartListRulesToFilters } from '../utils/list-utils';
import { useRouter } from 'next/router';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import { useWithUserContext } from '../hooks/useWithUser';
import { useDispatchAction } from '../hooks/useDispatchAction';
import { hookDeepEqual } from '../hooks/util';
import WithItemFilters from '../components/Filters/FilterContext';
import selectList from '../selectors/selectList';
import useStyles from '../components/ListDetail/ListDetail.styles';
import ListDetailDialog from '../components/ListDetail/ListDetailDialog';
import ListItems from '../components/ListDetail/ListItems';
import { useNetworks } from '../hooks/useStateMetadata';
import { FilterParams } from '../utils/searchFilters';
import useMemoCompare from '../hooks/useMemoCompare';
import { usePrevious } from '../hooks/usePrevious';
import SmartListDialog from '../components/Dialogs/SmartListDialog';
import PublicListDialog from '../components/Dialogs/PublicListDialog';

interface ListDetailDialogProps {
  list?: List;
  openDeleteConfirmation: boolean;
}

interface ListDetailProps {
  readonly preloaded?: boolean;
}

function ListDetail(props: ListDetailProps) {
  const classes = useStyles();

  const { isLoggedIn } = useWithUserContext();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | undefined>();
  const [deleted, setDeleted] = useState(false);
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [publicListDialogOpen, setPublicListDialogOpen] = useState(false);
  const [publicListOptionsOpen, setPublicListOptionsOpen] = useState(false);
  const [smartListDialogOpen, setSmartListDialogOpen] = useState(false);
  const [publicList, setPublicList] = useState(false);
  const [deleteOnWatch, setDeleteOnWatch] = useState(false);
  const [newListName, setNewListName] = useState('');
  const router = useRouter();
  const networks = useNetworks();

  const listId = router.query.id as string;
  const previousListId = usePrevious(listId);

  const listLoading = useStateSelector(
    state => state.lists.loading[LIST_RETRIEVE_INITIATED],
  );

  const [list, previousList] = useStateSelectorWithPrevious(
    state => selectList(state, listId),
    hookDeepEqual,
  );

  let defaultFilters: FilterParams = useMemoCompare(
    () => {
      if (list && networks) {
        return smartListRulesToFilters(list, networks || []);
      } else {
        return {};
      }
    },
    [list, networks],
    hookDeepEqual,
  );

  const [showFilter, setShowFilter] = useState(false);

  const dispatchUpdateList = useDispatchAction(updateList);
  const dispatchGetList = useDispatchAction(getList);

  const handleModalClose = useCallback(() => {
    setSmartListDialogOpen(false);
    setPublicListDialogOpen(false);
    setPublicListOptionsOpen(false);
  }, []);

  const openSmartListDialog = useCallback(() => {
    setSmartListDialogOpen(true);
  }, []);

  const openPublicListDialog = useCallback(() => {
    setPublicListDialogOpen(true);
  }, []);

  const openPublicListOptions = useCallback(() => {
    setPublicListOptionsOpen(true);
  }, []);

  const handleRenameList = () => {
    if (isLoggedIn) {
      dispatchUpdateList({
        listId: listId,
        name: newListName,
      });
    }

    setRenameDialogOpen(false);
  };

  const handleMakeListPublic = () => {
    if (isLoggedIn) {
      // dispatchUpdateList({
      //   listId: listId,
      //   name: newListName,
      // });
    }

    setPublicListDialogOpen(false);
  };

  const handleRenameChange = event => {
    setNewListName(event.target.value);
  };

  //
  // Effects
  //

  useEffect(() => {
    if ((previousListId || !props.preloaded) && listId !== previousListId) {
      dispatchGetList({ listId });
    }
  }, [listId, previousListId, props.preloaded]);

  useEffect(() => {
    if (!previousList && list) {
      setDeleteOnWatch(prev => {
        let removeWatchedItemsOption =
          list?.configuration?.options?.removeWatchedItems;
        if (_.isUndefined(removeWatchedItemsOption)) {
          return prev;
        } else {
          return removeWatchedItemsOption;
        }
      });
    }
  }, [list]);

  //
  // State updaters
  //

  const handleMenu = event => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(undefined);
  };

  const setWatchedSetting = () => {
    if (list) {
      const listOptions = list.configuration?.options || {};
      let newListOptions = {
        ...listOptions,
        removeWatchedItems: !deleteOnWatch,
      };

      dispatchUpdateList({
        listId,
        options: newListOptions,
      });

      setDeleteOnWatch(prev => !prev);
    }
  };

  const handleRenameModalOpen = () => {
    handleClose();
    setRenameDialogOpen(true);
  };

  const toggleFilters = () => {
    setShowFilter(prev => !prev);
  };

  //
  // Render
  //
  const renderLoading = () => {
    return (
      <div style={{ display: 'flex' }}>
        <div style={{ flexGrow: 1 }}>
          <LinearProgress />
        </div>
      </div>
    );
  };

  const renderRenameDialog = (list: List) => {
    if (!list) {
      return;
    }

    return (
      <div>
        <Dialog
          open={renameDialogOpen}
          onClose={() => setRenameDialogOpen(false)}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title" className={classes.title}>
            {`Update "${list?.name}" List Name?`}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Naming is hard, update your list name:
            </DialogContentText>
            <FormControl className={classes.formControl}>
              <TextField
                label="List Name"
                defaultValue={list.name}
                className={classes.textField}
                margin="normal"
                onChange={handleRenameChange}
              />
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setRenameDialogOpen(false)}>Cancel</Button>
            <Button
              onClick={handleRenameList}
              color="primary"
              variant="contained"
              autoFocus
            >
              Update
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  };

  const renderMakeListPublic = (list: List) => {
    if (!list) {
      return;
    }

    return (
      <div>
        <Dialog
          open={publicListOptionsOpen}
          onClose={() => setPublicListDialogOpen(false)}
          aria-labelledby="alert-dialog-public-list"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-public-list" className={classes.title}>
            {`Make "${list?.name}" List Public?`}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Making this list public will allow anyone with the URL to view
              your list.
            </DialogContentText>
            <FormControlLabel
              control={
                <Switch
                  checked={list.isPublic}
                  onChange={() => handleMakeListPublic()}
                  value="checked"
                  color="primary"
                />
              }
              label="Public List"
            />
            <FormControl style={{ width: '100%' }}>
              <TextField
                autoFocus
                margin="dense"
                id="name"
                label="URL"
                type="text"
                fullWidth
                value={window.location.href}
                InputProps={{
                  readOnly: true,
                }}
                className={classes.urlField}
              />
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setPublicListOptionsOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleMakeListPublic}
              color="primary"
              variant="contained"
              autoFocus
            >
              Update
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  };

  const renderProfileMenu = () => {
    if (!isLoggedIn) {
      return null;
    }

    if (!list?.ownedByRequester) {
      return null;
    }

    let isMenuOpen = !_.isUndefined(anchorEl);

    return (
      <div>
        <IconButton
          aria-owns={isMenuOpen ? 'material-appbar' : undefined}
          aria-haspopup="true"
          color="inherit"
          onClick={handleMenu}
        >
          <Settings />
          <Typography variant="srOnly">Settings</Typography>
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          open={isMenuOpen}
          onClose={handleClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={handleRenameModalOpen}>
            <ListItemIcon>
              <Edit />
            </ListItemIcon>
            Rename List
          </MenuItem>
          <MenuItem onClick={() => setDeleteConfirmationOpen(true)}>
            <ListItemIcon>
              <Delete />
            </ListItemIcon>
            Delete List
          </MenuItem>
          <MenuItem>
            <FormControlLabel
              control={
                <Switch
                  checked={deleteOnWatch}
                  onChange={setWatchedSetting}
                  value="checked"
                  color="primary"
                />
              }
              label="Automatically remove items after watching"
            />
          </MenuItem>
          <MenuItem onClick={() => setPublicListOptionsOpen(true)}>
            <ListItemIcon>
              <Public />
            </ListItemIcon>
            {`Make list ${list.isPublic ? 'Private' : 'Public'}`}
          </MenuItem>
        </Menu>
      </div>
    );
  };

  const renderListDetail = (list: List) => {
    if ((!listLoading && !list) || deleted) {
      router.replace('/');
    }

    return (
      <div className={classes.root}>
        <div className={classes.listContainer}>
          <div className={classes.listHeader}>
            <div className={classes.listNameContainer}>
              <Typography
                variant="h4"
                align="left"
                className={classes.listName}
              >
                {list.name}
                {list.isPublic ? (
                  <Tooltip title="This list is public" placement="top">
                    <IconButton onClick={openPublicListDialog} size="small">
                      <Public className={classes.icon} />
                    </IconButton>
                  </Tooltip>
                ) : null}
                {list.isDynamic ? (
                  <Tooltip title="This is a Smart List" placement="top">
                    <IconButton onClick={openSmartListDialog} size="small">
                      <OfflineBolt className={classes.icon} />
                    </IconButton>
                  </Tooltip>
                ) : null}
              </Typography>
            </div>
            <ShowFiltersButton onClick={toggleFilters} />
            {renderProfileMenu()}
          </div>
          <WithItemFilters initialFilters={{ ...defaultFilters }}>
            <ListItems showFilter={showFilter} listId={listId} />
          </WithItemFilters>
        </div>
        <ListDetailDialog
          list={list}
          openDeleteConfirmation={deleteConfirmationOpen}
        />
        <SmartListDialog
          open={smartListDialogOpen}
          onClose={handleModalClose}
        />
        <PublicListDialog
          open={publicListDialogOpen}
          onClose={handleModalClose}
        />
        {renderRenameDialog(list)}
        {renderMakeListPublic(list)}
      </div>
    );
  };

  return list && networks ? (
    <ScrollToTopContainer>{renderListDetail(list!)}</ScrollToTopContainer>
  ) : (
    renderLoading()
  );
}

// DEBUG only.
// ListDetail.whyDidYouRender = true;

export default React.memo(ListDetail, _.isEqual);
